/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.git4idea.ssh;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class allows accessing information in OpenSSH configuration file.
 */
public class SSHConfig {
  /**
   * Entry list for config
   */
  final List<HostEntry> myEntries = new ArrayList<HostEntry>();
  /**
   * User home directory
   */
  @NonNls private final static String USER_HOME = System.getProperty("user.home");
  /**
   * Allowed authentication methods
   */
  @NonNls private final static HashSet<String> ALLOWED_METHODS = new HashSet<String>();

  static {
    ALLOWED_METHODS.add(SSHMain.PUBLICKEY_METHOD);
    ALLOWED_METHODS.add(SSHMain.KEYBOARD_INTERACTIVE_METHOD);
    ALLOWED_METHODS.add(SSHMain.PASSWORD_METHOD);
  }

  /**
   * Lookup host in the host database
   *
   * @param user a user name
   * @param host a host name
   * @param port a port
   * @return a create host entry
   */
  public
  @NotNull
  Host lookup(@Nullable String user, @NotNull String host, @Nullable Integer port) {
    final Host rc = new Host();
    entriesLoop:
    for (HostEntry e : myEntries) {
      for (Pattern p : e.myNegative) {
        if (p.matcher(host).matches()) {
          continue entriesLoop;
        }
      }
      if (e.myExactPositive.contains(host)) {
        Host.merge(e.myHost, rc, rc);
        continue;
      }
      for (Pattern p : e.myPositive) {
        if (p.matcher(host).matches()) {
          Host.merge(rc, e.myHost, rc);
        }
      }
    }
    // force user specified user and host names
    if (user != null) {
      rc.myUser = user;
    }
    if (port != null) {
      rc.myPort = port;
    }
    if (rc.myHostName == null) {
      rc.myHostName = host;
    }
    rc.setDefaults();
    return rc;
  }


  /**
   * @return a SSH user configuration file
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public static SSHConfig load() throws IOException {
    SSHConfig rc = new SSHConfig();
    File configFile = new File(USER_HOME + File.separatorChar + ".ssh", "config");
    if (!configFile.exists()) {
      // no config file = empty config file
      return rc;
    }
    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "ISO-8859-1"));
    try {
      Host host = null;
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
          continue;
        }
        final String[] parts = line.split("[ \t]*[= \t]", 2);
        final String keyword = parts[0];
        final String arg = unqouteIfNeeded(parts[1]);
        if ("Host".equalsIgnoreCase(keyword)) {
          HostEntry entry = new HostEntry(arg);
          rc.myEntries.add(entry);
          host = entry.myHost;
          continue;
        }
        if (host == null) {
          // if the value is outside of the host section, ignore it
          continue;
        }
        if ("BatchMode".equalsIgnoreCase(keyword)) {
          host.myBatchMode = parseBoolean(arg);
        }
        else if ("HostKeyAlgorithms".equalsIgnoreCase(keyword)) {
          host.myHostKeyAlgorithms = Collections.unmodifiableList(parseList(arg));
        }
        else if ("HostKeyAlias".equalsIgnoreCase(keyword)) {
          host.myHostKeyAlias = arg;
        }
        else if ("HostName".equalsIgnoreCase(keyword)) {
          host.myHostName = arg;
        }
        else if ("IdentityFile".equalsIgnoreCase(keyword)) {
          host.myIdentityFile = arg;
        }
        else if ("NumberOfPasswordPrompts".equalsIgnoreCase(keyword)) {
          host.myNumberOfPasswordPrompts = parseInt(arg);
        }
        else if ("PasswordAuthentication".equalsIgnoreCase(keyword)) {
          host.myPasswordAuthentication = parseBoolean(arg);
        }
        else if ("Port".equalsIgnoreCase(keyword)) {
          host.myPort = parseInt(arg);
        }
        else if ("PreferredAuthentications".equalsIgnoreCase(keyword)) {
          final LinkedList<String> list = parseList(arg);
          list.retainAll(ALLOWED_METHODS);
          if (!list.isEmpty()) {
            host.myPreferredMethods = Collections.unmodifiableList(list);
          }
        }
        else if ("PubkeyAuthentication".equalsIgnoreCase(keyword)) {
          host.myPubkeyAuthentication = parseBoolean(arg);
        }
        else if ("User".equalsIgnoreCase(keyword)) {
          host.myUser = arg;
        }
      }
    }
    finally {
      in.close();
    }
    return rc;
  }

  /**
   * Parse integer and return null if integer is incorrect
   *
   * @param arg an argument to parse
   * @return {@link Integer} or null.
   */
  private static Integer parseInt(final String arg) {
    try {
      return Integer.parseInt(arg);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Parse file name handling %d %u %l %h %r options
   *
   * @param arg a file name to parse
   * @return actual file name
   */
  private static File parseFileName(Host host, final String arg) {
    try {
      StringBuilder rc = new StringBuilder();
      for (int i = 0; i < arg.length(); i++) {
        char ch = arg.charAt(i);
        if (i == 0 && ch == '~') {
          rc.append(USER_HOME);
          continue;
        }
        if (ch == '%' && i + 1 < arg.length()) {
          //noinspection AssignmentToForLoopParameter
          i++;
          switch (arg.charAt(i)) {
            case '%':
              rc.append('%');
              break;
            case 'd':
              rc.append(USER_HOME);
              break;
            case 'h':
              rc.append(host.getHostName());
              break;
            case 'l':
              rc.append(InetAddress.getLocalHost().getHostName());
              break;
            case 'r':
              rc.append(host.getUser());
              break;
            default:
              rc.append('%');
              rc.append(ch);
              break;
          }
        }
        rc.append(ch);
      }
      return new File(rc.toString());
    }
    catch (UnknownHostException e) {
      return null;
    }
  }

  /**
   * Parse booleas avlue
   *
   * @param arg an value to parse
   * @return a boolean value
   */
  private static Boolean parseBoolean(final String arg) {
    //noinspection HardCodedStringLiteral
    return "yes".equals(arg);
  }

  private static LinkedList<String> parseList(final String arg) {
    LinkedList<String> values = new LinkedList<String>();
    for (String a : arg.split("[ \t,]+")) {
      if (a.length() == 0) {
        continue;
      }
      values.add(a);
    }
    return values;
  }

  /**
   * Unquote string if needed
   *
   * @param part a string to unqoute
   * @return unquoted value
   */
  private static String unqouteIfNeeded(String part) {
    if (part.length() > 1 && part.charAt(0) == '"' && part.charAt(part.length() - 1) == '"') {
      part = part.substring(1, part.length() - 1);
    }
    return part.trim();
  }


  /**
   * Host entry in the file
   */
  private static class HostEntry {
    /**
     * Negative patterns
     */
    private final List<Pattern> myNegative = new ArrayList<Pattern>();
    /**
     * Positive patterns
     */
    private final List<Pattern> myPositive = new ArrayList<Pattern>();
    /**
     * Exact positve patterns that match host excatly rather than by mask
     */
    private final List<String> myExactPositive = new ArrayList<String>();
    /**
     * The host entry
     */
    private Host myHost = new Host();

    /**
     * Create host entry from patterns
     *
     * @param patterns a patterns to match
     */
    public HostEntry(final String patterns) {
      for (String pattern : patterns.split("[\t ,]+")) {
        if (pattern.length() == 0) {
          continue;
        }
        if (pattern.startsWith("!")) {
          myNegative.add(compilePattern(pattern.substring(1).trim()));
        }
        else if (pattern.indexOf('?') == 0 && pattern.indexOf('*') == 0) {
          myExactPositive.add(pattern);
        }
        else {
          myPositive.add(compilePattern(pattern));
        }
      }
    }

    /**
     * Convert host pattern from glob format to regexp. Note that the function assumes
     * a valid host pattern, so characters that might not happen in the host name but
     * must be escaped in Req exp are not escaped.
     *
     * @param s a pattern to convert
     * @return the resulting pattern
     */
    private static Pattern compilePattern(final String s) {
      StringBuilder rc = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        switch (ch) {
          case '?':
            rc.append('.');
            break;
          case '*':
            rc.append(".*");
            break;
          case '.':
            // for non-host strings more characters
            rc.append('\\');
          default:
            rc.append(ch);
        }
      }
      return Pattern.compile(rc.toString());
    }
  }

  /**
   * Host information
   */
  public static class Host {
    /**
     * User name
     */
    @Nullable private String myUser;
    /**
     * The name of the host
     */
    @Nullable private String myHostName;
    /**
     * The port nummber
     */
    @Nullable private Integer myPort;
    /**
     * Identy file
     */
    @Nullable private String myIdentityFile;
    /**
     * Preferred authentication methods
     */
    @Nullable private List<String> myPreferredMethods;
    /**
     * Preferred authentication methods
     */
    @Nullable private List<String> myHostKeyAlgorithms;
    /**
     * Bantch mode parameter
     */
    @Nullable private Boolean myBatchMode;
    /**
     * Alias for host key
     */
    @Nullable private String myHostKeyAlias;
    /**
     * Number of pasword prompts
     */
    @Nullable private Integer myNumberOfPasswordPrompts;
    /**
     * If true password authentication is enabled
     */
    @Nullable private Boolean myPasswordAuthentication;
    /**
     * If true, public key authentication is allowed
     */
    @Nullable private Boolean myPubkeyAuthentication;

    /**
     * @return remote user name
     */
    @NotNull
    public String getUser() {
      return notNull(myUser);
    }

    /**
     * @return real host name
     */
    @NotNull
    public String getHostName() {
      return notNull(myHostName);
    }

    /**
     * @return port number
     */
    @SuppressWarnings({"NullableProblems"})
    public int getPort() {
      return notNull(myPort).intValue();
    }

    /**
     * @return identity file or null if the default for algorithm should be used
     */
    @Nullable
    public File getIdentityFile() {
      if (myIdentityFile == null) {
        return null;
      }
      return parseFileName(this, myIdentityFile);
    }

    /**
     * @return preffered authentication methods
     */
    @NotNull
    public List<String> getPreferredMethods() {
      return notNull(myPreferredMethods);
    }

    /**
     * @return true if the host should be use in the batch mode
     */
    @SuppressWarnings({"NullableProblems"})
    public boolean isBatchMode() {
      return notNull(myBatchMode).booleanValue();
    }

    /**
     * @return alghorithms that should be used for the host
     */
    @NotNull
    public List<String> getHostKeyAlgorithms() {
      return notNull(myHostKeyAlgorithms);
    }

    /**
     * @return the number of the password prompts
     */
    @SuppressWarnings({"NullableProblems"})
    public int getNumberOfPasswordPrompts() {
      return notNull(myNumberOfPasswordPrompts).intValue();
    }

    /**
     * @return alias for host to be used in known hosts file
     */
    @NotNull
    public String getHostKeyAlias() {
      return notNull(myHostKeyAlias);
    }

    /**
     * @return true if password authentication is supported for the host
     */
    public boolean supportsPasswordAuthentication() {
      return notNull(myPasswordAuthentication).booleanValue();
    }

    /**
     * @return true if public key authentication is supported for the host
     */
    public boolean supportsPubkeyAuthentication() {
      return notNull(myPubkeyAuthentication).booleanValue();
    }

    /**
     * Set defaults for unspecifed fields
     */
    @SuppressWarnings({"HardCodedStringLiteral"})
    private void setDefaults() {
      if (myUser == null) {
        myUser = System.getProperty("user.name");
      }
      if (myPort == null) {
        myPort = 22;
      }
      if (myPreferredMethods == null) {
        myPreferredMethods = Collections
          .unmodifiableList(Arrays.asList(SSHMain.PUBLICKEY_METHOD, SSHMain.KEYBOARD_INTERACTIVE_METHOD, SSHMain.PASSWORD_METHOD));
      }
      if (myBatchMode == null) {
        myBatchMode = Boolean.FALSE;
      }
      if (myHostKeyAlgorithms == null) {
        myHostKeyAlgorithms = Collections.unmodifiableList(Arrays.asList(SSHMain.SSH_RSA_ALGORITHM, SSHMain.SSH_DSS_ALGORITHM));
      }
      if (myNumberOfPasswordPrompts == null) {
        myNumberOfPasswordPrompts = 3;
      }
      if (myHostKeyAlias == null) {
        myHostKeyAlias = myHostName;
      }
      if (myPasswordAuthentication == null) {
        myPasswordAuthentication = Boolean.TRUE;
      }
      if (myPubkeyAuthentication == null) {
        myPubkeyAuthentication = Boolean.TRUE;
      }
    }

    /**
     * Enusre that the value is not null
     *
     * @param value the value to check
     * @param <T>   the parameter type
     * @return the provided argument if not null
     * @throws IllegalStateException if the value is null
     */
    @NotNull
    private static <T> T notNull(@Nullable T value) {
      if (value == null) {
        throw new IllegalStateException("The value must not be null");
      }
      return value;
    }

    /**
     * Merge information from two host entries
     *
     * @param first  this entry is the preferred
     * @param second this entry provides information only if {@code first) did not have one
     * @param result this entry is updated as result of merge (usually {@code first) or {@code second))
     */
    private static void merge(Host first, Host second, Host result) {
      result.myUser = mergeValue(first.myUser, second.myUser);
      result.myHostName = mergeValue(first.myHostName, second.myHostName);
      result.myPort = mergeValue(first.myPort, second.myPort);
      result.myIdentityFile = mergeValue(first.myIdentityFile, second.myIdentityFile);
      result.myPreferredMethods = mergeValue(first.myPreferredMethods, second.myPreferredMethods);
      result.myHostKeyAlgorithms = mergeValue(first.myHostKeyAlgorithms, second.myHostKeyAlgorithms);
      result.myBatchMode = mergeValue(first.myBatchMode, second.myBatchMode);
      result.myHostKeyAlias = mergeValue(first.myHostKeyAlias, second.myHostKeyAlias);
      result.myNumberOfPasswordPrompts = mergeValue(first.myNumberOfPasswordPrompts, second.myNumberOfPasswordPrompts);
      result.myPasswordAuthentication = mergeValue(first.myPasswordAuthentication, second.myPasswordAuthentication);
      result.myPubkeyAuthentication = mergeValue(first.myPubkeyAuthentication, second.myPubkeyAuthentication);
    }

    /**
     * Merge single value
     *
     * @param first  the first value
     * @param second the second value
     * @param <T>    a value type
     * @return a result of merge
     */
    private static <T> T mergeValue(T first, T second) {
      return first == null ? second : first;
    }
  }
}
