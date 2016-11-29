/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import git4idea.GitFormatException;
import git4idea.GitVcs;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Parses the 'git log' output basing on the given number of options.
 * Doesn't execute of prepare the command itself, performs only parsing.</p>
 *
 * <p>
 * Usage:
 * 1. Pass options you want to have in the output to the constructor using the {@link GitLogOption} enum constants.
 * 2. Get the custom format pattern for 'git log' by calling {@link #getPretty()}
 * 3. Call the command and retrieve the output.
 * 4. Parse the output via {@link #parse(String)} or {@link #parseOneRecord(String)} (if you want the output to be parsed line by line).</p>
 *
 * <p>The class is package visible, since it's used only in GitHistoryUtils - the class which retrieve various pieced of history information
 * in different formats from 'git log'</p>
 *
 * <p>Note that you may pass one set of options to the GitLogParser constructor and then execute git log with other set of options.
 * In that case {@link #parse(String)} will parse only those options which you've specified in the constructor.
 * Others will be ignored since the parser knows nothing about them: it just gets the 'git log' output to parse.
 * Moreover you really <b>must</b> use {@link #getPretty()} to pass "--pretty=format" pattern to 'git log' - otherwise the parser won't be able
 * to parse output of 'git log' (because special separator characters are used for that).</p>
 *
 * <p>If you use '--name-status' or '--name-only' flags in 'git log' you also <b>must</b> call {@link #parseStatusBeforeName(boolean)} with
 * true or false respectively, because it also affects the output.</p>
 *  
 * @see git4idea.history.GitLogRecord
 */
public class GitLogParser {
  // Single records begin with %x01, end with %03. Items of commit information (hash, committer, subject, etc.) are separated by %x02.
  // each character is declared twice - for Git pattern format and for actual character in the output.
  public static final String RECORD_START = "\u0001";
  public static final String ITEMS_SEPARATOR = "\u0002";
  public static final String RECORD_END = "\u0003";
  public static final String RECORD_START_GIT = "%x01";
  private static final String ITEMS_SEPARATOR_GIT = "%x02";
  private static final String RECORD_END_GIT = "%x03";

  private final String myFormat;  // pretty custom format generated in the constructor
  private final GitLogOption[] myOptions;
  private final boolean mySupportsRawBody;
  private final NameStatus myNameStatusOption;

  /**
   * Record format:
   *
   * One git log record.
   * RECORD_START - optional: it is split out when calling parse() but it is not when calling parseOneRecord() directly.
   * commit information separated by ITEMS_SEPARATOR.
   * RECORD_END
   * Optionally: changed paths or paths with statuses (if --name-only or --name-status options are given).
   *
   * Example:
   * 2c815939f45fbcfda9583f84b14fe9d393ada790<ITEM_SEPARATOR>sample commit<RECORD_END>
   * D       a.txt
   */
  private static final Pattern ONE_RECORD = Pattern.compile(RECORD_START + "?(.*)" + RECORD_END + "\n*(.*)", Pattern.DOTALL);
  private static final String SINGLE_PATH = "([^\t\r\n]+)"; // something not empty, not a tab or newline.
  private static final String EOL = "\\s*(?:\r|\n|\r\n)";
  private static final String PATHS =
    SINGLE_PATH +                    // First path - required.
    "(?:\t" + SINGLE_PATH + ")?" +   // Second path - optional. Paths are separated by tab.
    "(?:" + EOL + ")?";                             // Path(s) information ends with a line terminator (possibly except the last path in the output).

  private static Pattern NAME_ONLY = Pattern.compile(PATHS);
  private static Pattern NAME_STATUS = Pattern.compile("([\\S]+)\t" + PATHS);

  // --name-only, --name-status or no flag
  enum NameStatus {
    /** No flag. */
    NONE,
    /** --name-only */
    NAME,
    /** --name-status */
    STATUS
  }

  /**
   * Options which may be passed to 'git log --pretty=format:' as placeholders and then parsed from the result.
   * These are the pieces of information about a commit which we want to get from 'git log'.
   */
  enum GitLogOption {
    HASH("H"), COMMIT_TIME("ct"), AUTHOR_NAME("an"), AUTHOR_TIME("at"), AUTHOR_EMAIL("ae"), COMMITTER_NAME("cn"),
    COMMITTER_EMAIL("ce"), SUBJECT("s"), BODY("b"), PARENTS("P"), REF_NAMES("d"), SHORT_REF_LOG_SELECTOR("gd"),
    RAW_BODY("B");

    private String myPlaceholder;
    GitLogOption(String placeholder) { myPlaceholder = placeholder; }
    private String getPlaceholder() { return myPlaceholder; }
  }

  /**
   * Constructs new parser with the given options and no names of changed files in the output.
   */
  GitLogParser(Project project, GitLogOption... options) {
    this(project, NameStatus.NONE, options);
  }

  /**
   * Constructs new parser with the specified options.
   * Only these options will be parsed out and thus will be available from the GitLogRecord.
   */
  GitLogParser(Project project, NameStatus nameStatusOption, GitLogOption... options) {
    myFormat = makeFormatFromOptions(options);
    myOptions = options;
    myNameStatusOption = nameStatusOption;
    GitVcs vcs = GitVcs.getInstance(project);
    mySupportsRawBody = vcs != null && GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(vcs.getVersion());
  }

  private static String makeFormatFromOptions(GitLogOption[] options) {
    Function<GitLogOption,String> function = new Function<GitLogOption, String>() {
      @Override public String fun(GitLogOption option) {
        return "%" + option.getPlaceholder();
      }
    };
    return RECORD_START_GIT + StringUtil.join(options, function, ITEMS_SEPARATOR_GIT) + RECORD_END_GIT;
  }

  String getPretty() {
    return "--pretty=format:" + myFormat;
  }

  /**
   * Parses the output returned from 'git log' which was executed with '--pretty=format:' pattern retrieved from {@link #getPretty()}.
   * @param output 'git log' output to be parsed.
   * @return The list of {@link GitLogRecord GitLogRecords} with information for each revision.
   *         The list is sorted as usual for git log - the first is the newest, the last is the oldest.
   */
  @NotNull
  List<GitLogRecord> parse(@NotNull String output) {
    // Here is what git log returns for --pretty=tformat:^%H#%s$
    // ^2c815939f45fbcfda9583f84b14fe9d393ada790#sample commit$
    //
    // D       a.txt
    // ^b71477e9738168aa67a8d41c414f284255f81e8a#moved out$
    //
    // R100    dir/anew.txt    anew.txt
    final List<String> records = StringUtil.split(output, RECORD_START); // split by START, because END is the end of information, but not the end of the record: file status and path follow.
    String notMatchedPart = null;
    final List<GitLogRecord> res = new ArrayList<>(records.size());
    for (String record : records) {
      if (!record.trim().isEmpty()) {  // record[0] is empty for sure, because we're splitting on RECORD_START. Just to play safe adding the check for all records.
        if (notMatchedPart != null) {
          record = notMatchedPart + record;
        }
        if (ONE_RECORD.matcher(record).matches()) {
          notMatchedPart = null;
          res.add(parseOneRecord(record));
        }
        else {
          notMatchedPart = record;
        }
      }
    }
    return res;
  }

  /**
   * Parses a single record returned by 'git log'. The record contains information from pattern and file status and path (if respective
   * flags --name-only or name-status were provided).
   * @param line record to be parsed.
   * @return GitLogRecord with information about the revision or {@code null} if the given line is empty.
   * @throws GitFormatException if the line is given in unexpected format.
   */
  @Nullable
  GitLogRecord parseOneRecord(@NotNull String line) {
    if (line.isEmpty()) {
      return null;
    }
    Matcher matcher = ONE_RECORD.matcher(line);
    if (!matcher.matches()) {
      throwGFE("ONE_RECORD didn't match", line);
    }
    String commitInfo = matcher.group(1);
    if (commitInfo == null) {
      throwGFE("No match for group#1 in", line);
    }

    final Map<GitLogOption, String> res = parseCommitInfo(commitInfo);

    // parsing status and path (if given)
    final List<String> paths = new ArrayList<>(1);
    final List<GitLogStatusInfo> statuses = new ArrayList<>();

    if (myNameStatusOption != NameStatus.NONE) {
      String pathsAndStatuses = matcher.group(2);
      if (pathsAndStatuses == null) {
        throwGFE("No match for group#2 in", line);
      }

      if (myNameStatusOption == NameStatus.NAME) {
        Matcher pathsMatcher = NAME_ONLY.matcher(pathsAndStatuses);
        while (pathsMatcher.find()) {
          String path1 = pathsMatcher.group(1);
          String path2 = pathsMatcher.group(2);
          assertNotNull(path1, "path", pathsAndStatuses);
          paths.add(path1);
          if (path2 != null) { // null is perfectly legal here: second path is given only in case of rename
            paths.add(path2);
          }
        }
      }
      else {
        Matcher nameStatusMatcher = NAME_STATUS.matcher(pathsAndStatuses);
        while (nameStatusMatcher.find()) {
          String status = nameStatusMatcher.group(1);
          String path1 = nameStatusMatcher.group(2);
          String path2 = nameStatusMatcher.group(3);
          assertNotNull(status, "status", pathsAndStatuses);
          assertNotNull(path1, "path1", pathsAndStatuses);
          paths.add(path1);
          if (path2 != null) {
            paths.add(path2);
          }
          statuses.add(new GitLogStatusInfo(GitChangeType.fromString(status), path1, path2));
        }
      }
    }
    return new GitLogRecord(res, paths, statuses, mySupportsRawBody);
  }


  @NotNull
  private Map<GitLogOption, String> parseCommitInfo(@NotNull String commitInfo) {
    // parsing revision information
    // we rely on the order of options
    final String[] values = commitInfo.split(ITEMS_SEPARATOR);
    final Map<GitLogOption, String> res = new HashMap<>(values.length);
    int i = 0;
    for (; i < values.length && i < myOptions.length; i++) {  // fill valid values
      res.put(myOptions[i], values[i]);
    }
    for (; i < myOptions.length; i++) {  // options which were not returned are set to blank string, extra options are ignored.
      res.put(myOptions[i], "");
    }
    return res;
  }

  private static void assertNotNull(String value, String valueName, String line) {
    if (value == null) {
      throwGFE("Unexpectedly null " + valueName + " in ", line);
    }
  }

  private static void throwGFE(String message, String line) {
    throw new GitFormatException(message + " [" + StringUtil.escapeStringCharacters(line) + "]");
  }

}
