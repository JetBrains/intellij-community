// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * One record (commit information) returned by git log output.
 * The access methods try heavily to return some default value if real is unavailable, for example, blank string is better than null.
 * BUT if one tries to get an option which was not specified to the GitLogParser, one will get null.
 *
 * @see GitLogParser
 */
class GitLogRecord {
  private static final Logger LOG = Logger.getInstance(GitLogRecord.class);

  @NotNull protected final Map<GitLogParser.GitLogOption, String> myOptions;
  protected final boolean mySupportsRawBody;

  protected GitHandler myHandler;

  GitLogRecord(@NotNull Map<GitLogParser.GitLogOption, String> options,
               boolean supportsRawBody) {
    myOptions = options;
    mySupportsRawBody = supportsRawBody;
  }

  @NotNull
  private String lookup(@NotNull GitLogParser.GitLogOption key) {
    String value = myOptions.get(key);
    if (value == null) {
      LOG.error("Missing value for option " + key + ", while executing " + myHandler);
      return "";
    }
    return value;
  }

  // trivial access methods
  @NotNull
  String getHash() {
    return lookup(HASH);
  }

  @NotNull
  String getTreeHash() {
    return lookup(TREE);
  }

  @NotNull
  String getAuthorName() {
    return lookup(AUTHOR_NAME);
  }

  @NotNull
  String getAuthorEmail() {
    return lookup(AUTHOR_EMAIL);
  }

  @NotNull
  String getCommitterName() {
    return lookup(COMMITTER_NAME);
  }

  @NotNull
  String getCommitterEmail() {
    return lookup(COMMITTER_EMAIL);
  }

  @NotNull
  String getSubject() {
    return lookup(SUBJECT);
  }

  @NotNull
  String getBody() {
    return lookup(BODY);
  }

  @NotNull
  String getRawBody() {
    return lookup(RAW_BODY);
  }

  @NotNull
  String getShortenedRefLog() {
    return lookup(SHORT_REF_LOG_SELECTOR);
  }

  // access methods with some formatting or conversion

  @NotNull
  Date getDate() {
    return new Date(getCommitTime());
  }

  long getCommitTime() {
    try {
      return GitLogUtil.parseTime(myOptions.get(COMMIT_TIME));
    }
    catch (NumberFormatException e) {
      LOG.error("Couldn't get commit time from " + this + ", while executing " + myHandler, e);
      return 0;
    }
  }

  long getAuthorTimeStamp() {
    try {
      return GitLogUtil.parseTime(myOptions.get(AUTHOR_TIME));
    }
    catch (NumberFormatException e) {
      LOG.error("Couldn't get author time from " + this + ", while executing " + myHandler, e);
      return 0;
    }
  }

  String getFullMessage() {
    return (mySupportsRawBody ? getRawBody() : getSubject() + "\n\n" + getBody()).stripTrailing();
  }

  String @NotNull [] getParentsHashes() {
    final String parents = lookup(PARENTS);
    if (parents.trim().length() == 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    return parents.split(" ");
  }

  @NotNull
  public Collection<String> getRefs() {
    final String decorate = myOptions.get(REF_NAMES);
    return parseRefNames(decorate);
  }

  @NotNull
  public Map<GitLogParser.GitLogOption, String> getOptions() {
    return myOptions;
  }

  public boolean isSupportsRawBody() {
    return mySupportsRawBody;
  }

  @NotNull
  private static List<String> parseRefNames(@Nullable final String decoration) {
    if (decoration == null) {
      return ContainerUtil.emptyList();
    }
    final int startParentheses = decoration.indexOf("(");
    final int endParentheses = decoration.lastIndexOf(")");
    if ((startParentheses == -1) || (endParentheses == -1)) return Collections.emptyList();
    String refs = decoration.substring(startParentheses + 1, endParentheses);
    String[] names = refs.split(", ");
    List<String> result = new ArrayList<>();
    for (String item : names) {
      final String POINTER = " -> ";   // HEAD -> refs/heads/master in Git 2.4.3+
      if (item.contains(POINTER)) {
        List<String> parts = StringUtil.split(item, POINTER);
        result.addAll(ContainerUtil.map(parts, String::trim));
      }
      else {
        int colon = item.indexOf(':'); // tags have the "tag:" prefix.
        String raw = colon > 0 ? item.substring(colon + 1).trim() : item;
        result.add(raw);
      }
    }
    return result;
  }

  /**
   * for debugging purposes - see {@link GitUtil#parseTimestampWithNFEReport(String, GitHandler, String)}.
   */
  public void setUsedHandler(GitHandler handler) {
    myHandler = handler;
  }

  @NonNls
  @Override
  public String toString() {
    return String.format("GitLogRecord{myOptions=%s, mySupportsRawBody=%s, myHandler=%s}",
                         myOptions, mySupportsRawBody, myHandler);
  }
}
