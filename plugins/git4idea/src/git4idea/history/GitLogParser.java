// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import git4idea.GitFormatException;
import git4idea.GitUtil;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Parses the 'git log' output basing on the given number of options.
 * Doesn't execute of prepare the command itself, performs only parsing.</p>
 * <p>
 * Usage:<ol>
 * <li> Pass options you want to have in the output to the constructor using the {@link GitLogOption} enum constants.
 * <li> Get the custom format pattern for 'git log' by calling {@link #getPretty()}
 * <li> Call the command and retrieve the output.
 * <li> Parse the output via {@link #parseLine(CharSequence)}(prefered), {@link #parse(CharSequence)} or {@link #parseOneRecord(CharSequence)}.
 * Note that {@link #parseLine(CharSequence)} expects lines without line separators</ol></p>
 * <p>Note that you may pass one set of options to the GitLogParser constructor and then execute git log with other set of options.
 * In that case {@link #parse(CharSequence)} will likely fail with {@link GitFormatException}.
 * Moreover you really <b>must</b> use {@link #getPretty()} to pass "--pretty=format" pattern to 'git log' -- otherwise the parser won't be able
 * to parse output of 'git log' (because special separator characters are used for that).</p>
 * <p>Commit records have the following format:
 * <pre>
 * RECORD_START (commit information, separated by ITEMS_SEPARATOR) RECORD_END \n (changed paths with statuses)?</pre>
 * Example:
 * <pre>
 * 2c815939f45fbcfda9583f84b14fe9d393ada790&lt;ITEMS_SEPARATOR&gt;sample commit&lt;RECORD_END&gt;
 * D       a.txt</pre></p>
 *
 * @see GitLogRecord
 */
@ApiStatus.Internal
public class GitLogParser<R extends GitLogRecord> {
  private static final Logger LOG = Logger.getInstance(GitLogParser.class);

  // Single records begin with %x01%x01, end with %03%03. Items of commit information (hash, committer, subject, etc.) are separated by %x02%x02.
  static final String RECORD_START = "\u0001\u0001";
  static final String ITEMS_SEPARATOR = "\u0002\u0002";
  static final String RECORD_END = "\u0003\u0003";
  private static final int MAX_SEPARATOR_LENGTH = 10;
  private static final char[] CONTROL_CHARS = new char[]{'\u0001', '\u0002', '\u0003'};
  private static final int INPUT_ERROR_MESSAGE_HEAD_LIMIT = 1000000; // limit the string by ~2mb
  private static final int INPUT_ERROR_MESSAGE_TAIL_LIMIT = 100;

  private static final AtomicInteger ERROR_COUNT = new AtomicInteger();

  private final boolean mySupportsRawBody;
  @NotNull private final String myPretty;

  @NotNull private final OptionsParser myOptionsParser;
  @NotNull private final PathsParser<R> myPathsParser;

  @NotNull private final GitLogRecordBuilder<R> myRecordBuilder;

  private final String myRecordStart;
  private final String myRecordEnd;
  private final String myItemsSeparator;

  private boolean myIsInBody = true;

  private GitLogParser(@NotNull GitLogRecordBuilder<R> recordBuilder,
                       boolean supportsRawBody,
                       @NotNull NameStatus nameStatusOption,
                       GitLogOption @NotNull ... options) {
    mySupportsRawBody = supportsRawBody;
    myRecordBuilder = recordBuilder;

    myRecordStart = RECORD_START + generateRandomSequence();
    myRecordEnd = RECORD_END + generateRandomSequence();
    myItemsSeparator = ITEMS_SEPARATOR + generateRandomSequence();

    myPretty = "--pretty=format:" + makeFormatFromOptions(options);

    myOptionsParser = new OptionsParser(options);
    myPathsParser = new MyPathsParser(nameStatusOption);
  }

  public GitLogParser(@NotNull Project project,
                      @NotNull GitLogRecordBuilder<R> recordBuilder,
                      @NotNull NameStatus nameStatus,
                      GitLogOption @NotNull ... options) {
    this(recordBuilder, GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(project), nameStatus, options);
  }

  @NotNull
  public static GitLogParser<GitLogFullRecord> createDefaultParser(@NotNull Project project,
                                                                   @NotNull NameStatus nameStatus,
                                                                   GitLogOption @NotNull ... options) {
    return new GitLogParser<>(project, new DefaultGitLogFullRecordBuilder(), nameStatus, options);
  }

  @NotNull
  public static GitLogParser<GitLogRecord> createDefaultParser(@NotNull Project project,
                                                               GitLogOption @NotNull ... options) {
    return new GitLogParser<>(project, new DefaultGitLogRecordBuilder(), NameStatus.NONE, options);
  }

  @NotNull
  public List<R> parse(@NotNull CharSequence output) {
    List<R> result = new ArrayList<>();

    List<CharSequence> lines = StringUtil.split(output, "\n", true, false);
    for (CharSequence line : lines) {
      try {
        R record = parseLine(line);
        if (record != null) {
          result.add(record);
        }
      }
      catch (GitFormatException e) {
        clear();
        LOG.error(e);
      }
    }

    R record = finish();
    if (record != null) result.add(record);

    return result;
  }

  @Nullable
  public R parseOneRecord(@NotNull CharSequence output) {
    List<R> records = parse(output);
    clear();
    if (records.isEmpty()) return null;
    return ContainerUtil.getFirstItem(records);
  }

  /**
   * Expects a line without separator.
   */
  @Nullable
  public R parseLine(@NotNull CharSequence line) {
    if (myPathsParser.expectsPaths()) {
      return parseLineWithPaths(line);
    }
    return parseLineWithoutPaths(line);
  }

  @Nullable
  private R parseLineWithPaths(@NotNull CharSequence line) {
    if (myIsInBody) {
      myIsInBody = !myOptionsParser.parseLine(line);
    }
    else {
      if (CharArrayUtil.regionMatches(line, 0, myRecordStart)) {
        R record = createRecord();
        myIsInBody = !myOptionsParser.parseLine(line);
        return record;
      }

      myPathsParser.parseLine(line);
    }

    return null;
  }

  @Nullable
  private R parseLineWithoutPaths(@NotNull CharSequence line) {
    if (myOptionsParser.parseLine(line)) {
      return createRecord();
    }
    return null;
  }

  @Nullable
  public R finish() {
    if (myOptionsParser.isEmpty()) return null;
    return createRecord();
  }

  @Nullable
  private R createRecord() {
    if (myPathsParser.getErrorText() != null ||
        !myOptionsParser.hasCompleteOptionsList()) {
      if (myPathsParser.getErrorText() != null) LOG.debug("Creating record was skipped: " + myPathsParser.getErrorText());
      if (!myOptionsParser.hasCompleteOptionsList()) LOG.debug("Parsed incomplete options " + myOptionsParser.myResult.getResult() + " for " +
                                                               Arrays.toString(myOptionsParser.myOptions));
      myOptionsParser.clear();
      myRecordBuilder.clear();
      myPathsParser.clear();
      return null;
    }

    Map<GitLogOption, String> options = myOptionsParser.getResult();
    myOptionsParser.clear();

    R record = myRecordBuilder.build(options, mySupportsRawBody);
    myRecordBuilder.clear();
    myPathsParser.clear();
    myIsInBody = true;

    return record;
  }

  public void clear() {
    myOptionsParser.clear();
    myRecordBuilder.clear();
    myIsInBody = true;
  }

  @NotNull
  public String getPretty() {
    return myPretty;
  }

  @NotNull
  private String makeFormatFromOptions(GitLogOption @NotNull [] options) {
    return encodeForGit(myRecordStart) + makeFormatFromOptions(options, encodeForGit(myItemsSeparator)) + encodeForGit(myRecordEnd);
  }

  @NotNull
  public static String makeFormatFromOptions(GitLogOption @NotNull [] options, @NotNull String separator) {
    return StringUtil.join(options, option -> "%" + option.getPlaceholder(), separator);
  }

  @NotNull
  private static String encodeForGit(@NotNull String line) {
    StringBuilder encoded = new StringBuilder();
    line.chars().forEachOrdered(c -> encoded.append("%x").append(String.format("%02x", c)));
    return encoded.toString();
  }

  @NotNull
  private static String generateRandomSequence() {
    int length = ERROR_COUNT.get() % (MAX_SEPARATOR_LENGTH - RECORD_START.length());
    StringBuilder tail = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int randomIndex = ThreadLocalRandom.current().nextInt(0, CONTROL_CHARS.length);
      tail.append(CONTROL_CHARS[randomIndex]);
    }
    return tail.toString();
  }

  private static void throwGFE(@NotNull String message, @NotNull CharSequence line) {
    ERROR_COUNT.incrementAndGet();
    throw new GitFormatException(message + " [" + getTruncatedEscapedOutput(line) + "]");
  }

  @NotNull
  private static String getTruncatedEscapedOutput(@NotNull CharSequence line) {
    String lineString;

    String formatString = "%s...(%d more characters)...%s";
    if (line.length() > INPUT_ERROR_MESSAGE_HEAD_LIMIT + INPUT_ERROR_MESSAGE_TAIL_LIMIT + formatString.length()) {
      lineString = String.format(formatString, line.subSequence(0, INPUT_ERROR_MESSAGE_HEAD_LIMIT),
                                 (line.length() - INPUT_ERROR_MESSAGE_HEAD_LIMIT - INPUT_ERROR_MESSAGE_TAIL_LIMIT),
                                 line.subSequence(line.length() - INPUT_ERROR_MESSAGE_TAIL_LIMIT, line.length()));
    }
    else {
      lineString = line.toString();
    }

    return StringUtil.escapeStringCharacters(lineString);
  }

  // --name-status or no flag
  enum NameStatus {
    /**
     * No flag.
     */
    NONE,
    /**
     * --name-status
     */
    STATUS
  }

  /**
   * Options which may be passed to 'git log --pretty=format:' as placeholders and then parsed from the result.
   * These are the pieces of information about a commit which we want to get from 'git log'.
   */
  @ApiStatus.Internal
  public enum GitLogOption {
    HASH("H"), TREE("T"), COMMIT_TIME("ct"), AUTHOR_NAME("an"), AUTHOR_TIME("at"), AUTHOR_EMAIL("ae"), COMMITTER_NAME("cn"),
    COMMITTER_EMAIL("ce"), SUBJECT("s"), BODY("b"), PARENTS("P"), REF_NAMES("d"), SHORT_REF_LOG_SELECTOR("gd"),
    RAW_BODY("B");

    private final String myPlaceholder;

    GitLogOption(@NonNls String placeholder) {
      myPlaceholder = placeholder;
    }

    @NonNls
    private String getPlaceholder() {
      return myPlaceholder;
    }
  }

  private class OptionsParser {
    private final GitLogOption @NotNull [] myOptions;
    @NotNull private final PartialResult myResult = new PartialResult();

    OptionsParser(GitLogOption @NotNull [] options) {
      myOptions = options;
    }

    public boolean parseLine(@NotNull CharSequence line) {
      int offset = 0;

      if (myResult.isEmpty()) {
        if (!CharArrayUtil.regionMatches(line, offset, myRecordStart)) {
          return false;
        }
        offset += myRecordStart.length();
      }

      while (offset < line.length()) {
        if (atRecordEnd(line, offset)) {
          myResult.finishItem();
          if (!hasCompleteOptionsList()) {
            throwGFE("Parsed incomplete options " + myResult.getResult() + " for " +
                     Arrays.toString(myOptions), line);
          }
          return true;
        }

        if (CharArrayUtil.regionMatches(line, offset, myItemsSeparator)) {
          myResult.finishItem();
          offset += myItemsSeparator.length();
        }
        else {
          char c = line.charAt(offset);
          myResult.append(c);
          offset++;
        }
      }

      myResult.append('\n');

      return false;
    }

    public boolean hasCompleteOptionsList() {
      return myResult.getResult().size() == myOptions.length;
    }

    private boolean atRecordEnd(@NotNull CharSequence line, int offset) {
      return (offset == line.length() - myRecordEnd.length() && CharArrayUtil.regionMatches(line, offset, myRecordEnd));
    }

    @NotNull
    public Map<GitLogOption, String> getResult() {
      return createOptions(myResult.getResult());
    }

    @NotNull
    private Map<GitLogOption, String> createOptions(@NotNull List<String> options) {
      Map<GitLogOption, String> optionsMap = new HashMap<>(options.size());
      for (int index = 0; index < options.size(); index++) {
        optionsMap.put(myOptions[index], options.get(index));
      }
      return optionsMap;
    }

    public void clear() {
      myResult.clear();
    }

    public boolean isEmpty() {
      return myResult.isEmpty();
    }
  }

  public static class PathsParser<R extends GitLogRecord> {
    @NotNull private final NameStatus myNameStatusOption;
    @NotNull private final GitLogRecordBuilder<R> myRecordBuilder;
    @Nullable private String myErrorText = null;

    PathsParser(@NotNull NameStatus nameStatusOption, @NotNull GitLogRecordBuilder<R> recordBuilder) {
      myNameStatusOption = nameStatusOption;
      myRecordBuilder = recordBuilder;
    }

    public void parseLine(@NotNull CharSequence line) {
      if (line.length() == 0) return;

      List<String> match = parsePathsLine(line);

      if (match.isEmpty()) {
        // ignore
      }
      else {
        if (myNameStatusOption != NameStatus.STATUS) throwGFE("Status list not expected", line);

        if (match.size() < 2) {
          myErrorText = getErrorText(line);
        }
        else {
          if (match.size() == 2) {
            addPath(match.get(0), match.get(1), null);
          }
          else {
            addPath(match.get(0), match.get(1), match.get(2));
          }
        }
      }
    }

    @NotNull
    protected String getErrorText(@NotNull CharSequence line) {
      return "Could not parse status line [" + line + "]";
    }

    private void addPath(@NotNull String type, @NotNull String firstPath, @Nullable String secondPath) {
      myRecordBuilder.addPath(GitChangesParser.getChangeType(GitChangeType.fromString(type)), tryUnescapePath(firstPath),
                              tryUnescapePath(secondPath));
    }

    @Nullable
    @Contract("!null -> !null")
    private static String tryUnescapePath(@Nullable String path) {
      if (path == null) return null;
      try {
        return GitUtil.unescapePath(path);
      }
      catch (VcsException e) {
        LOG.error(e);
        return path;
      }
    }

    @NotNull
    private static List<String> parsePathsLine(@NotNull CharSequence line) {
      int offset = 0;
      List<String> result = new ArrayList<>();

      while (offset < line.length()) {
        int tokenEnd = StringUtil.indexOf(line, '\t', offset);
        if (tokenEnd == -1) tokenEnd = line.length();

        result.add(line.subSequence(offset, tokenEnd).toString());

        offset = tokenEnd + 1;
      }

      return result;
    }

    public boolean expectsPaths() {
      return myNameStatusOption == NameStatus.STATUS;
    }

    public void clear() {
      myErrorText = null;
    }

    @Nullable
    public String getErrorText() {
      return myErrorText;
    }
  }

  private class MyPathsParser extends PathsParser<R> {
    MyPathsParser(@NotNull NameStatus nameStatusOption) {
      super(nameStatusOption, myRecordBuilder);
    }

    @NotNull
    @Override
    protected String getErrorText(@NotNull CharSequence line) {
      return super.getErrorText(line) + " for record " + myOptionsParser.myResult.getResult();
    }
  }

  private static class PartialResult {
    @NotNull private List<String> myResult = new ArrayList<>();
    @NotNull private final StringBuilder myCurrentItem = new StringBuilder();

    public void append(char c) {
      myCurrentItem.append(c);
    }

    public void finishItem() {
      myResult.add(myCurrentItem.toString());
      myCurrentItem.setLength(0);
    }

    @NotNull
    public List<String> getResult() {
      return myResult;
    }

    public void clear() {
      myCurrentItem.setLength(0);
      myResult = new ArrayList<>();
    }

    public boolean isEmpty() {
      return myResult.isEmpty() && myCurrentItem.length() == 0;
    }
  }
}
