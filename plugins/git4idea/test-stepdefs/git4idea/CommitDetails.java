/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.dvcs.test.Executor.echo;
import static com.intellij.dvcs.test.Executor.touch;
import static git4idea.GitCucumberWorld.virtualCommits;
import static git4idea.test.GitExecutor.git;

/**
 * 
 * @author Kirill Likhodedov
 */
public class CommitDetails {

  private String myHash;
  private String myMessage;
  private String myAuthor;
  private Collection<Change> myChanges;

  private static class Change {

    public void apply() {
      switch (myType) {
        case MODIFIED:
          echo(myFile, myContent);
          break;
        case ADDED:
          touch(myFile, myContent);
          break;
        case DELETED:
          throw new UnsupportedOperationException("Not implemented yet");
        case MOVED:
          throw new UnsupportedOperationException("Not implemented yet");
      }
    }

    enum Type {
      MODIFIED, ADDED, DELETED, MOVED
    }

    private final Type myType;
    private final String myFile;
    private final String myContent;

    Change(Type type, String filename, String content) {
      myType = type;
      myFile = filename;
      myContent = content;
    }

  }

  private enum ParsingStage {
    MESSAGE,
    DATA,
    CHANGES
  }

  /**
   * Format:
   * <pre>
       commit subject

       and optional description
       -----
       Author: John Bro
       Changes:
       M file.txt "feature changes"
   * </pre>
   */
  public static CommitDetails parse(String hash, String details) {
    CommitDetails commit = new CommitDetails();
    commit.myHash = hash;

    StringBuilder message = new StringBuilder();
    Collection<Change> changes = new ArrayList<Change>();
    ParsingStage stage = ParsingStage.MESSAGE;
    for (String line : details.split("\n")) {
      if (line.equals("-----")) {
        stage = ParsingStage.DATA;
        continue;
      }
      else if (line.equals("Changes:")) {
        stage = ParsingStage.CHANGES;
        continue;
      }

      if (stage == ParsingStage.MESSAGE) {
        message.append(line);
      }
      else if (stage == ParsingStage.CHANGES) {
        changes.add(parseChange(line));
      }
      else if (line.toLowerCase().startsWith("author: ")) {
        commit.myAuthor = line.substring("author: ".length());
      }
    }

    commit.myMessage = message.toString();
    commit.myChanges = changes;
    return commit;
  }

  private static Change parseChange(String change) {
    int firstSpace = change.indexOf(' ');
    int secondSpace = change.indexOf(' ', firstSpace + 1);
    return new Change(parseType(change.substring(0, firstSpace)),
                      change.substring(firstSpace + 1, secondSpace),
                      change.substring(secondSpace + 1));
  }

  private static Change.Type parseType(String type) {
    if (type.equals("M")) {
      return Change.Type.MODIFIED;
    }
    else if (type.equals("A")) {
      return Change.Type.ADDED;
    }
    else if (type.equals("D")) {
      return Change.Type.DELETED;
    }
    else if (type.equals("R")) {
      return Change.Type.MOVED;
    }
    return null;
  }

  /**
   * @return real commit details.
   */
  public CommitDetails apply() {
    for (Change change : myChanges) {
      change.apply();
    }

    String commitOutput = git(String.format("commit -am '%1$s' --author '%2$s <%2$s@example.com>'", myMessage, myAuthor));
    CommitDetails realCommit = parseHashFromCommitOutput(commitOutput);
    virtualCommits.register(myHash, realCommit);
    return realCommit;
  }

  CommitDetails parseHashFromCommitOutput(String commitOutput) {
    String line = commitOutput.split("\n")[0];
    Pattern reg = Pattern.compile("^\\s*\\[.+ ([a-fA-F0-9]+)\\] (.+)$");
    Matcher matcher = reg.matcher(line);
    boolean matches = matcher.matches();
    assert matches;
    return new CommitDetails().hash(matcher.group(1)).message(matcher.group(2));
  }

  private CommitDetails hash(String hash) {
    myHash = hash;
    return this;
  }

  private CommitDetails message(String message) {
    myMessage = message;
    return this;
  }

  public String getHash() {
    return myHash;
  }

  public String getMessage() {
    return myMessage;
  }

}
