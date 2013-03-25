package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
abstract class AndroidBuildTestingCommandExecutor implements AndroidBuildTestingManager.MyCommandExecutor {
  public static final String ENTRY_HEADER = "______ENTRY_";

  private volatile StringWriter myStringWriter = new StringWriter();
  private final Map<String, Pattern> myPathPatterns = new HashMap<String, Pattern>();

  private final Set<String> myCheckedJars = new HashSet<String>();

  public void addPathPrefix(@NotNull String id, @NotNull String prefix) {
    myPathPatterns.put(id, Pattern.compile("(" + FileUtil.toSystemIndependentName(prefix) + ").*"));
  }

  public void addRegexPathPattern(@NotNull String id, @NotNull String regex) {
    myPathPatterns.put(id, Pattern.compile("(" + regex + ")"));
  }

  public void addRegexPathPatternPrefix(@NotNull String id, @NotNull String regex) {
    myPathPatterns.put(id, Pattern.compile("(" + regex + ").*"));
  }

  @NotNull
  @Override
  public Process createProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment) {
    startNewEntry();
    final String[] argsToLog = processArgs(args);
    logString(StringUtil.join(argsToLog, "\n"));

    if (environment.size() > 0) {
      final StringBuilder envBuilder = new StringBuilder();

      for (Map.Entry<? extends String, ? extends String> entry : environment.entrySet()) {
        if (envBuilder.length() > 0) {
          envBuilder.append(", ");
        }
        final String value = progessArg(entry.getValue());
        envBuilder.append(entry.getKey()).append("=").append(value);
      }
      logString("\nenv: " + envBuilder.toString());
    }
    logString("\n\n");
    try {
      return doCreateProcess(args, environment);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void log(@NotNull String s) {
    startNewEntry();
    final String[] args = s.split("\\n");
    logString(StringUtil.join(processArgs(args), "\n"));
    logString("\n\n");
  }

  @Override
  public void checkJarContent(@NotNull String jarId, @NotNull String jarPath) {
    doCheckJar(jarId, jarPath);
    myCheckedJars.add(jarId);
  }

  protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
  }

  private synchronized void startNewEntry() {
    logString(ENTRY_HEADER + "\n");
  }

  private synchronized void logString(String s) {
    myStringWriter.write(s);
  }

  private String[] processArgs(String[] args) {
    final String[] result = new String[args.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = progessArg(args[i]);
    }
    return result;
  }

  private String progessArg(String arg) {
    String s = FileUtil.toSystemIndependentName(arg);

    for (Map.Entry<String, Pattern> entry : myPathPatterns.entrySet()) {
      final Pattern prefixPattern = entry.getValue();
      final String id = entry.getKey();
      final Matcher matcher = prefixPattern.matcher(s);

      if (matcher.matches()) {
        s = "$" + id + "$" + s.substring(matcher.group(1).length());
      }
    }
    return s;
  }

  @NotNull
  protected abstract Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
    throws Exception;

  @NotNull
  public synchronized String getLog() {
    return myStringWriter.toString();
  }

  public synchronized void clear() {
    myStringWriter = new StringWriter();
    myCheckedJars.clear();
  }

  @NotNull
  protected Set<String> getCheckedJars() {
    return myCheckedJars;
  }

  public static String normalizeExpectedLog(@NotNull String expectedLog, @NotNull String actualLog) {

    final String[] actualEntries = actualLog.split(AndroidBuildTestingCommandExecutor.ENTRY_HEADER);
    final List<String> actualEntryList = new ArrayList<String>();

    for (String entry : actualEntries) {
      final String s = entry.trim();

      if (s.length() == 0) {
        continue;
      }
      actualEntryList.add(s);
    }
    final Map<String, MyLogEntry> id2logEntry = new HashMap<String, MyLogEntry>();
    final String[] entries = expectedLog.split(AndroidBuildTestingCommandExecutor.ENTRY_HEADER);

    for (String entry : entries) {
      final String s = entry.trim();

      if (s.length() == 0) {
        continue;
      }
      final int newLineIdx = s.indexOf('\n');
      final int colonIdx = s.indexOf(':');
      assert colonIdx >= 0 && colonIdx < newLineIdx;

      final String id = s.substring(0, colonIdx);
      final String depIdsStr = s.substring(colonIdx + 1, newLineIdx);
      final String[] depIds = depIdsStr.length() > 0
                              ? depIdsStr.trim().split(",")
                              : ArrayUtil.EMPTY_STRING_ARRAY;
      final String content = s.substring(newLineIdx + 1).trim();
      id2logEntry.put(id, new MyLogEntry(content, depIds));
    }
    final List<String> addedEntries = new ArrayList<String>();
    final Set<String> addedEntriesSet = new HashSet<String>();

    while (addedEntries.size() < id2logEntry.size()) {
      final List<String> candidates = new ArrayList<String>();

      for (Map.Entry<String, MyLogEntry> entry : id2logEntry.entrySet()) {
        final String id = entry.getKey();

        if (addedEntriesSet.contains(id)) {
          continue;
        }
        final MyLogEntry logEntry = entry.getValue();
        boolean canBeAdded = true;

        for (String depId : logEntry.myDepIds) {
          if (!addedEntriesSet.contains(depId)) {
            canBeAdded = false;
            break;
          }
        }

        if (canBeAdded) {
          candidates.add(id);
        }
      }

      if (candidates.size() == 0) {
        throw new RuntimeException("The log graph contains cycles");
      }
      boolean added = false;

      if (actualEntryList.size() > addedEntries.size()) {
        final String actualEntryContent = actualEntryList.get(addedEntries.size());

        for (String id : candidates) {
          if (id2logEntry.get(id).myContent.equals(actualEntryContent)) {
            addedEntries.add(id);
            addedEntriesSet.add(id);
            added = true;
            break;
          }
        }
      }

      if (!added) {
        addedEntriesSet.addAll(candidates);
        addedEntries.addAll(candidates);
      }
    }
    final StringBuilder builder = new StringBuilder();

    for (String id : addedEntries) {
      final MyLogEntry entry = id2logEntry.get(id);
      builder.append(entry.myContent).append("\n\n");
    }
    return builder.toString();
  }

  public static String normalizeLog(@NotNull String log) {
    final String[] entries = log.split(AndroidBuildTestingCommandExecutor.ENTRY_HEADER);
    final StringBuilder result = new StringBuilder();

    for (String entry : entries) {
      final String s = entry.trim();

      if (s.length() == 0) {
        continue;
      }
      result.append(s).append("\n\n");
    }
    return result.toString();
  }

  protected static class MyProcess extends Process {

    private final int myExitValue;
    private final String myOutputText;
    private final String myErrorText;

    protected MyProcess(int exitValue, @NotNull String outputText, @NotNull String errorText) {
      myExitValue = exitValue;
      myOutputText = outputText;
      myErrorText = errorText;
    }

    @Override
    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() {
      return stringToInputStream(myOutputText);
    }

    @Override
    public InputStream getErrorStream() {
      return stringToInputStream(myErrorText);
    }

    private static ByteArrayInputStream stringToInputStream(String s) {
      return new ByteArrayInputStream(s.getBytes(Charset.defaultCharset()));
    }

    @Override
    public int waitFor() throws InterruptedException {
      return exitValue();
    }

    @Override
    public int exitValue() {
      return myExitValue;
    }

    @Override
    public void destroy() {
    }
  }

  private static class MyLogEntry {
    final String myContent;
    final String[] myDepIds;

    private MyLogEntry(@NotNull String content, @NotNull String[] depIds) {
      myContent = content;
      myDepIds = depIds;
    }
  }
}
