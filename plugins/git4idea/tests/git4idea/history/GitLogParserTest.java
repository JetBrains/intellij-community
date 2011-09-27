/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import git4idea.GitUtil;
import git4idea.tests.GitTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static git4idea.history.GitLogParser.*;
import static git4idea.history.GitLogParser.GitLogOption.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Test for {@link GitLogParser}.
 *
 * @author Kirill Likhodedov
 */
public class GitLogParserTest extends GitTest {

  public static final GitLogOption[] GIT_LOG_OPTIONS =
    new GitLogOption[]{SHORT_HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
      COMMITTER_EMAIL, SUBJECT, BODY, SHORT_PARENTS, PARENTS, RAW_BODY
    };
  private VirtualFile myRoot;
  private GitLogParser myParser;
  private GitTestLogRecord myRecord;

  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();
    myRoot = new LightVirtualFile();
    myRecord = new GitTestLogRecord();
  }

  @Test
  public void parseOneRecordWithoutNameStatus() throws VcsException {
    myParser = new GitLogParser(myProject, GIT_LOG_OPTIONS);
    doTest(GitTestLogRecord.NameStatusOption.NONE);
  }
  
  @Test
  public void parseOneRecordWithName() throws VcsException {
    myParser = new GitLogParser(myProject, NameStatus.NAME,  GIT_LOG_OPTIONS);
    doTest(GitTestLogRecord.NameStatusOption.NAME);
  }

  @Test
  public void parseOneRecordWithNameStatus() throws VcsException {
    myParser = new GitLogParser(myProject, NameStatus.STATUS, GIT_LOG_OPTIONS);
    doTest(GitTestLogRecord.NameStatusOption.STATUS);
  }
  
  private void doTest(GitTestLogRecord.NameStatusOption option) throws VcsException {
    String s = myRecord.prepareOutputLine(option);
    GitLogRecord record = myParser.parseOneRecord(s);
    assertRecord(record, myRecord, option);
  }

  private void assertRecord(GitLogRecord actual, GitTestLogRecord expected, GitTestLogRecord.NameStatusOption option) throws VcsException {
    assertEquals(actual.getHash(), expected.myHash);
    assertEquals(actual.getShortHash(), expected.shortHash());
    
    assertEquals(actual.getCommitterName(), expected.myCommitterName);
    assertEquals(actual.getCommitterEmail(), expected.myCommitterEmail);
    assertEquals(actual.getDate(), expected.myCommitTime);
    
    assertEquals(actual.getAuthorName(), expected.myAuthorName);
    assertEquals(actual.getAuthorEmail(), expected.myAuthorEmail);
    assertEquals(actual.getAuthorTimeStamp(), expected.myAuthorTime.getTime() / 1000);
    
    assertEquals(actual.getAuthorAndCommitter(), GitUtil.adjustAuthorName(String.format("%s <%s>", expected.myAuthorName, expected.myAuthorEmail),
                                                                          String.format("%s <%s>", expected.myCommitterName, expected.myCommitterEmail)));

    
    assertEquals(actual.getSubject(), expected.mySubject);
    assertEquals(actual.getBody(), expected.myBody);
    assertEquals(actual.getRawBody(), expected.rawBody());

    assertEquals(actual.getParentsHashes(), expected.myParents);
    assertEquals(actual.getParentsShortHashes(), expected.shortParents());
    
    if (option == GitTestLogRecord.NameStatusOption.NAME) {
      assertPaths(actual.getFilePaths(myRoot), expected.paths());
    } else if (option == GitTestLogRecord.NameStatusOption.STATUS) {
      assertPaths(actual.getFilePaths(myRoot), expected.paths());
      assertChanges(actual.coolChangesParser(myProject, myRoot), expected.changes());
    }
  }

  private void assertPaths(List<FilePath> actualPaths, List<String> expectedPaths) {
    assertEquals(actualPaths.size(), expectedPaths.size(), "Actual: " + actualPaths);
    for (FilePath actualPath : actualPaths) {
      String actualRelPath = FileUtil.getRelativePath(myRoot.getPath(), actualPath.getPath(), '/');
      assertTrue(expectedPaths.contains(actualRelPath));
    }
  }

  private void assertChanges(List<Change> actual, List<GitTestChange> expected) {
    assertEquals(actual.size(), expected.size());
    for (int i = 0; i < actual.size(); i++) {
      Change actualChange = actual.get(i);
      GitTestChange expectedChange = expected.get(i);
      assertChange(actualChange, expectedChange);
    }
  }

  private void assertChange(Change actualChange, GitTestChange expectedChange) {
    assertEquals(actualChange.getType(), expectedChange.myType);
    switch (actualChange.getType()) {
      case MODIFICATION:
      case MOVED:
        assertEquals(getBeforePath(actualChange), expectedChange.myBeforePath);
        assertEquals(getAfterPath(actualChange), expectedChange.myAfterPath);
        return;
      case NEW:
        assertEquals(getAfterPath(actualChange), expectedChange.myAfterPath);
        return;
      case DELETED:
        assertEquals(getBeforePath(actualChange), expectedChange.myBeforePath);
        return;
      default:
        throw new AssertionError();
    } 
  }

  private String getBeforePath(Change actualChange) {
    return FileUtil.getRelativePath(myRoot.getPath(), actualChange.getBeforeRevision().getFile().getPath(), '/');
  }

  private String getAfterPath(Change actualChange) {
    return FileUtil.getRelativePath(myRoot.getPath(), actualChange.getAfterRevision().getFile().getPath(), '/');
  }

  private static class GitTestLogRecord {
    private static final String PARENT1 = "c916c63b89d8fa81ebf23cc5cbcdb75e115623c7";
    private static final String PARENT2 = "7c1298fd1f93df414ce0d87128532f819de2cbd4";

    String myHash = "2c815939f45fbcfda9583f84b14fe9d393ada790";
    Date myCommitTime = new Date(1317027817L * 1000);
    Date myAuthorTime = new Date(1315471452L * 1000);
    String myAuthorName = "John Doe";
    String myAuthorEmail = "John.Doe@example.com";
    String myCommitterName = "Bob Smith";
    String myCommitterEmail = "Bob@site.com";
    
    String mySubject = "Commit message";
    String myBody = "Description goes here\n" +
                    "\n" + // empty line
                    "Then comes a long long description.\n" +
                    "Probably multilined.";

    String[] myParents = { PARENT1, PARENT2 };

    GitTestChange[] myChanges = {
      GitTestChange.moved("file2", "file3"),
      GitTestChange.added("readme.txt"),
      GitTestChange.modified("src/CClass.java"),
      GitTestChange.deleted("src/ChildAClass.java")
    };

    String shortHash() {
      return myHash.substring(0, 7);
    }
    
    String[] shortParents() {
      return new String[] { myParents[0].substring(0, 7), myParents[1].substring(0, 7) };
    }

    private String shortParentsAsString() {
      return StringUtil.join(shortParents(), " ");
    }

    private String parentsAsString() {
      return StringUtil.join(myParents, " ");
    }

    String rawBody() {
      return mySubject + "\n\n" + myBody;
    }

    List<GitTestChange> changes() {
      return Arrays.asList(myChanges);
    }

    private String changesAsString() {
      StringBuilder sb = new StringBuilder();
      for (GitTestChange change : myChanges) {
        sb.append(change.toOutputString());
      }
      return sb.toString();
    }

    public List<String> paths() {
      List<String> paths = new ArrayList<String>();
      for (GitTestChange change : myChanges) {
        switch (change.myType) {
          case MODIFICATION:
          case NEW:
            paths.add(change.myAfterPath);
            break;
          case DELETED:
            paths.add(change.myBeforePath);
            break;
          case MOVED:
            paths.add(change.myAfterPath);
            paths.add(change.myBeforePath);
            break;
          default:
            throw new AssertionError();
        }
      }
      return paths;
    }

    private String pathsAsString() {
      StringBuilder sb = new StringBuilder();
      for (String path : paths()) {
        sb.append(path).append("\n");
      }
      return sb.toString();
    }

    enum NameStatusOption {
      NONE, NAME, STATUS
    }
    
    String prepareOutputLine(NameStatusOption nameStatusOption) {
      StringBuilder sb = new StringBuilder(RECORD_START);
      for (GitLogOption option : GIT_LOG_OPTIONS) {
        sb.append(optionToValue(option)).append(ITEMS_SEPARATOR);
      }
      sb.append(RECORD_END);

      if (nameStatusOption == NameStatusOption.NAME) {
        sb.append("\n\n").append(pathsAsString());
      }
      else if (nameStatusOption == NameStatusOption.STATUS) {
        sb.append("\n\n").append(changesAsString());
      }

      return sb.toString();
    }

    private String optionToValue(GitLogOption option) {
      switch (option) {
        case HASH:
          return myHash;
        case SUBJECT:
          return mySubject;
        case BODY:
          return myBody;
        case RAW_BODY:
          return rawBody();
        case COMMIT_TIME:
          return String.valueOf(myCommitTime.getTime() / 1000);
        case SHORT_PARENTS:
          return shortParentsAsString();
        case SHORT_HASH:
          return shortHash();
        case AUTHOR_NAME:
          return myAuthorName;
        case AUTHOR_TIME:
          return String.valueOf(myAuthorTime.getTime() / 1000);
        case AUTHOR_EMAIL:
          return myAuthorEmail;
        case COMMITTER_NAME:
          return myCommitterName;
        case COMMITTER_EMAIL:
          return myCommitterEmail;
        case PARENTS:
          return parentsAsString();
        case REF_NAMES:
          break;
        case SHORT_REF_LOG_SELECTOR:
          break;
      }
      throw new AssertionError();
    }

  }
  
  private static class GitTestChange {
    final Change.Type myType;
    final String myBeforePath;
    final String myAfterPath;

    GitTestChange(Change.Type type, String beforePath, String afterPath) {
      myAfterPath = afterPath;
      myBeforePath = beforePath;
      myType = type;
    }

    static GitTestChange added(String path) {
      return new GitTestChange(Change.Type.NEW, null, path);
    }

    static GitTestChange deleted(String path) {
      return new GitTestChange(Change.Type.DELETED, path, null);
    }

    static GitTestChange modified(String path) {
      return new GitTestChange(Change.Type.MODIFICATION, path, path);
    }

    static GitTestChange moved(String before, String after) {
      return new GitTestChange(Change.Type.MOVED, before, after);
    }
    
    String toOutputString() {
      switch (myType) {
        case MOVED: return outputString("R100", myBeforePath, myAfterPath);
        case MODIFICATION: return outputString("M", myBeforePath, null);
        case DELETED: return outputString("D", myBeforePath, null);
        case NEW: return outputString("A", myAfterPath, null);
        default:
          throw new AssertionError();
      }
    }

    private static String outputString(@NotNull String type, @Nullable String beforePath, @Nullable String afterPath) {
      StringBuilder sb = new StringBuilder();
      sb.append(type).append("\t");
      if (beforePath != null) {
        sb.append(beforePath).append("\t");
      }
      if (afterPath != null) {
        sb.append(afterPath).append("\t");
      }
      sb.append("\n");
      return sb.toString();
    }
  }
  
}
