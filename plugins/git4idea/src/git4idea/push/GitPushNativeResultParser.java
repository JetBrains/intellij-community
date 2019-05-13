/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output received from git push and returns a result.
 * NB: It is assumed that only one ref is pushed => there is only one result in the output.
 *
 * Output format described by git-push man:
 * <pre>
 * The status of the push is output in tabular form, with each line representing the status of a single ref.
 * If --porcelain is used, then each line of the output is of the form:
 *
 *             &lt;flag&gt; \t &lt;from&gt;:&lt;to&gt; \t &lt;summary&gt; (&lt;reason&gt;)
 *
 * The status of up-to-date refs is shown only if --porcelain or --verbose option is used.
 *
 * flag
 *     A single character indicating the status of the ref:
 *     (space)
 *         for a successfully pushed fast-forward;
 *     +
 *         for a successful forced update;
 *     -
 *         for a successfully deleted ref;
 *     *
 *         for a successfully pushed new ref;
 *     !
 *         for a ref that was rejected or failed to push; and
 *     =
 *         for a ref that was up to date and did not need pushing.
 *
 * summary
 *     For a successfully pushed ref, the summary shows the old and new values of the ref in a form
 *     suitable for using as an argument to git log (this is <old>..<new> in most cases, and
 *     <old>...<new> for forced non-fast-forward updates).
 *
 *     For a failed update, more details are given:
 * rejected
 *     Git did not try to send the ref at all, typically because it is not a fast-forward and you
 *     did not force the update.
 *
 * remote rejected
 *     The remote end refused the update. Usually caused by a hook on the remote side, or because
 *     the remote repository has one of the following safety options in effect:
 *     receive.denyCurrentBranch (for pushes to the checked out branch), receive.denyNonFastForwards
 *     (for forced non-fast-forward updates), receive.denyDeletes or receive.denyDeleteCurrent. See
 *     git-config(1).
 *
 * remote failure
 *     The remote end did not report the successful update of the ref, perhaps because of a
 *     temporary error on the remote side, a break in the network connection, or other transient
 *     error.
 *
 * from
 *     The name of the local ref being pushed, minus its refs/<type>/ prefix. In the case of deletion,
 *     the name of the local ref is omitted.
 *
 * to
 *     The name of the remote ref being updated, minus its refs/<type>/ prefix.
 *
 * reason
 *     A human-readable explanation. In the case of successfully pushed refs, no explanation is needed.
 *     For a failed ref, the reason for failure is described.
 * </pre>
 */
public class GitPushNativeResultParser {

  private static final Logger LOG = Logger.getInstance(GitPushNativeResultParser.class);
  private static final Pattern PATTERN = Pattern.compile("^.*([ +\\-\\*!=])\t" +   // flag
                                                         "(\\S+):(\\S+)\t" +       // from:to
                                                         "([^(]+)" +               // summary maybe with a trailing space
                                                         "(?:\\((.+)\\))?.*$");    // reason
  private static final Pattern RANGE = Pattern.compile("[0-9a-f]+[\\.]{2,3}[0-9a-f]+");

  @NotNull
  public static List<GitPushNativeResult> parse(@NotNull List<String> output) {
    List<GitPushNativeResult> results = ContainerUtil.newArrayList();
    for (String line : output) {
      Matcher matcher = PATTERN.matcher(line);
      if (matcher.matches()) {
        results.add(parseRefResult(matcher, line));
      }
    }
    return results;
  }

  @Nullable
  private static GitPushNativeResult parseRefResult(Matcher matcher, String line) {
    String flag = matcher.group(1);
    String from = matcher.group(2);
    String to = matcher.group(3);
    String summary = matcher.group(4).trim(); // the summary can have a trailing space (to simplify the regexp)
    @Nullable String reason = matcher.group(5);

    GitPushNativeResult.Type type = parseType(flag);
    if (type == null) {
      LOG.error("Couldn't parse push result type from flag [" + flag + "] in [" + line + "]");
      return null;
    }
    if (matcher.groupCount() < 4) {
      return null;
    }
    String range = RANGE.matcher(summary).matches() ? summary : null;
    return new GitPushNativeResult(type, from, reason, range);
  }

  private static GitPushNativeResult.Type parseType(String flag) {
    switch(flag.charAt(0)) {
      case ' ' : return GitPushNativeResult.Type.SUCCESS;
      case '+' : return GitPushNativeResult.Type.FORCED_UPDATE;
      case '-' : return GitPushNativeResult.Type.DELETED;
      case '*' : return GitPushNativeResult.Type.NEW_REF;
      case '!' : return GitPushNativeResult.Type.REJECTED;
      case '=' : return GitPushNativeResult.Type.UP_TO_DATE;
    }
    return null;
  }
}
