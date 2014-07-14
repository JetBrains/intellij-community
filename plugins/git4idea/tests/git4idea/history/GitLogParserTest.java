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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.test.GitPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static git4idea.history.GitLogParser.*;
import static git4idea.history.GitLogParser.GitLogOption.*;

public class GitLogParserTest extends GitPlatformTest {

  public static final GitLogOption[] GIT_LOG_OPTIONS =
    new GitLogOption[]{HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
      COMMITTER_EMAIL, SUBJECT, BODY, PARENTS, PARENTS, RAW_BODY
    };
  private VirtualFile myRoot;
  private GitLogParser myParser;
  private GitTestLogRecord myRecord;

  private static final GitTestLogRecord RECORD1 = new GitTestLogRecord(ContainerUtil.<GitTestLogRecordInfo, Object>immutableMapBuilder()
    .put(GitTestLogRecordInfo.HASH,         "2c815939f45fbcfda9583f84b14fe9d393ada790")
    .put(GitTestLogRecordInfo.AUTHOR_TIME,  new Date(1317027817L * 1000))
    .put(GitTestLogRecordInfo.AUTHOR_NAME,  "John Doe")
    .put(GitTestLogRecordInfo.AUTHOR_EMAIL, "John.Doe@example.com")
    .put(GitTestLogRecordInfo.COMMIT_TIME,  new Date(1315471452L * 1000))
    .put(GitTestLogRecordInfo.COMMIT_NAME,  "Bob Smith")
    .put(GitTestLogRecordInfo.COMMIT_EMAIL, "Bob@site.com")
    .put(GitTestLogRecordInfo.SUBJECT,      "Commit message")
    .put(GitTestLogRecordInfo.BODY,         "Description goes here\n" +
                                            "\n" + // empty line
                                            "Then comes a long long description.\n" +
                                            "Probably multilined.")
    .put(GitTestLogRecordInfo.PARENTS,      new String[] {
                                              "c916c63b89d8fa81ebf23cc5cbcdb75e115623c7",
                                              "7c1298fd1f93df414ce0d87128532f819de2cbd4"})
    .put(GitTestLogRecordInfo.CHANGES,      new GitTestChange[]{
                                              GitTestChange.moved("file2", "file3"),
                                              GitTestChange.added("readme.txt"),
                                              GitTestChange.modified("src/CClass.java"),
                                              GitTestChange.deleted("src/ChildAClass.java")})
    .build());

  private static final GitTestLogRecord RECORD2 = new GitTestLogRecord(ContainerUtil.<GitTestLogRecordInfo, Object>immutableMapBuilder()
    .put(GitTestLogRecordInfo.HASH,         "c916c63b89d8fa81ebf23cc5cbcdb75e115623c7")
    .put(GitTestLogRecordInfo.AUTHOR_TIME,  new Date(1317027817L * 1000))
    .put(GitTestLogRecordInfo.AUTHOR_NAME,  "John Doe")
    .put(GitTestLogRecordInfo.AUTHOR_EMAIL, "John.Doe@example.com")
    .put(GitTestLogRecordInfo.COMMIT_TIME,  new Date(1315471452L * 1000))
    .put(GitTestLogRecordInfo.COMMIT_NAME,  "John Doe")
    .put(GitTestLogRecordInfo.COMMIT_EMAIL, "John.Doe@example.com")
    .put(GitTestLogRecordInfo.SUBJECT,      "Commit message")
    .put(GitTestLogRecordInfo.BODY,         "Small description")
    .put(GitTestLogRecordInfo.PARENTS,      new String[] { "7c1298fd1f93df414ce0d87128532f819de2cbd4" })
    .put(GitTestLogRecordInfo.CHANGES,      new GitTestChange[] { GitTestChange.modified("src/CClass.java") })
    .build());

  private static final GitTestLogRecord RECORD3 = new GitTestLogRecord(ContainerUtil.<GitTestLogRecordInfo, Object>immutableMapBuilder()
    .put(GitTestLogRecordInfo.HASH,         "c916c63b89d8fa81ebf23cc5cbcdb75e115623c7")
    .put(GitTestLogRecordInfo.AUTHOR_TIME, new Date(1317027817L * 1000))
    .put(GitTestLogRecordInfo.AUTHOR_NAME,  "John Doe")
    .put(GitTestLogRecordInfo.AUTHOR_EMAIL, "John.Doe@example.com")
    .put(GitTestLogRecordInfo.COMMIT_TIME,  new Date(1315471452L * 1000))
    .put(GitTestLogRecordInfo.COMMIT_NAME,  "John Doe")
    .put(GitTestLogRecordInfo.COMMIT_EMAIL, "John.Doe@example.com")
    .put(GitTestLogRecordInfo.SUBJECT,      "Commit message")
    .put(GitTestLogRecordInfo.BODY,         "Small description")
    .put(GitTestLogRecordInfo.PARENTS,      new String[] { "7c1298fd1f93df414ce0d87128532f819de2cbd4" })
    .put(GitTestLogRecordInfo.CHANGES,      new GitTestChange[] { GitTestChange.modified("src/CClass.java") })
    .build());
  public static final List<GitTestLogRecord> ALL_RECORDS = Arrays.asList(RECORD1, RECORD2, RECORD3);


  protected void setUp() throws Exception {
    super.setUp();
    myRoot = myProjectRoot;
    myRecord = RECORD1; // for single record tests
  }

  public void testparseAllWithoutNameStatus() throws VcsException {
    doTestAllRecords(GitTestLogRecord.NameStatusOption.NONE);
  }

  public void testparseAllWithName() throws VcsException {
    doTestAllRecords(GitTestLogRecord.NameStatusOption.NAME);
  }

  public void testparseAllWithNameStatus() throws VcsException {
    doTestAllRecords(GitTestLogRecord.NameStatusOption.STATUS);
  }

  private void doTestAllRecords(GitTestLogRecord.NameStatusOption nameStatusOption) throws VcsException {
    NameStatus option;
    switch (nameStatusOption) {
      case NONE:   option = NameStatus.NONE; break;
      case NAME:   option = NameStatus.NAME; break;
      case STATUS: option = NameStatus.STATUS; break;
      default: throw new AssertionError();
    }

    myParser = new GitLogParser(myProject, option, GIT_LOG_OPTIONS);
    String output = prepareOutputForAllRecords(nameStatusOption);
    List<GitLogRecord> actualRecords = myParser.parse(output);
    List<GitTestLogRecord> expectedRecords = ALL_RECORDS;
    assertAllRecords(actualRecords, expectedRecords, nameStatusOption);
  }

  public void testparseOneRecordWithoutNameStatus() throws VcsException {
    myParser = new GitLogParser(myProject, GIT_LOG_OPTIONS);
    doTestOneRecord(GitTestLogRecord.NameStatusOption.NONE);
  }

  public void testparseOneRecordWithName() throws VcsException {
    myParser = new GitLogParser(myProject, NameStatus.NAME,  GIT_LOG_OPTIONS);
    doTestOneRecord(GitTestLogRecord.NameStatusOption.NAME);
  }

  public void testparseOneRecordWithNameStatus() throws VcsException {
    myParser = new GitLogParser(myProject, NameStatus.STATUS, GIT_LOG_OPTIONS);
    doTestOneRecord(GitTestLogRecord.NameStatusOption.STATUS);
  }
  
  private void doTestOneRecord(GitTestLogRecord.NameStatusOption option) throws VcsException {
    String s = myRecord.prepareOutputLine(option);
    GitLogRecord record = myParser.parseOneRecord(s);
    assertRecord(record, myRecord, option);
  }

  private void assertAllRecords(List<GitLogRecord> actualRecords,
                                List<GitTestLogRecord> expectedRecords,
                                GitTestLogRecord.NameStatusOption nameStatusOption) throws VcsException {
    assertEquals(actualRecords.size(), expectedRecords.size());
    for (int i = 0; i < actualRecords.size(); i++) {
      assertRecord(actualRecords.get(i), expectedRecords.get(i), nameStatusOption);
    }
  }

  private static String prepareOutputForAllRecords(GitTestLogRecord.NameStatusOption nameStatusOption) {
    StringBuilder sb = new StringBuilder();
    for (GitTestLogRecord record : ALL_RECORDS) {
      sb.append(record.prepareOutputLine(nameStatusOption)).append("\n");
    }
    return sb.toString();
  }

  private void assertRecord(GitLogRecord actual, GitTestLogRecord expected, GitTestLogRecord.NameStatusOption option) throws VcsException {
    assertEquals(expected.getHash(), actual.getHash());

    assertEquals(expected.getCommitterName(), actual.getCommitterName());
    assertEquals(expected.getCommitterEmail(), actual.getCommitterEmail());
    assertEquals(expected.getCommitTime(), actual.getDate());

    assertEquals(expected.getAuthorName(), actual.getAuthorName());
    assertEquals(expected.getAuthorEmail(), actual.getAuthorEmail());
    assertEquals(expected.getAuthorTime().getTime(), actual.getAuthorTimeStamp());

    String expectedAuthorAndCommitter = GitUtil.adjustAuthorName(
                                                String.format("%s <%s>", expected.getAuthorName(), expected.getAuthorEmail()),
                                                String.format("%s <%s>", expected.getCommitterName(), expected.getCommitterEmail()));
    assertEquals(expectedAuthorAndCommitter, getAuthorAndCommitter(actual));


    assertEquals(expected.getSubject(), actual.getSubject());
    assertEquals(expected.getBody(), actual.getBody());
    assertEquals(expected.rawBody(), actual.getRawBody());

    assertSameElements(actual.getParentsHashes(), expected.getParents());

    if (option == GitTestLogRecord.NameStatusOption.NAME) {
      assertPaths(actual.getFilePaths(myRoot), expected.paths());
    } else if (option == GitTestLogRecord.NameStatusOption.STATUS) {
      assertPaths(actual.getFilePaths(myRoot), expected.paths());
      assertChanges(actual.parseChanges(myProject, myRoot), expected.changes());
    }
  }

  @NotNull
  String getAuthorAndCommitter(@NotNull GitLogRecord actual) {
    String author = String.format("%s <%s>", actual.getAuthorName(), actual.getAuthorEmail());
    String committer = String.format("%s <%s>", actual.getCommitterName(), actual.getCommitterEmail());
    return GitUtil.adjustAuthorName(author, committer);
  }

  private void assertPaths(List<FilePath> actualPaths, List<String> expectedPaths) {
    List<String> actual = ContainerUtil.map(actualPaths, new Function<FilePath, String>() {
      @Override
      public String fun(FilePath path) {
        return FileUtil.getRelativePath(new File(myProjectPath), path.getIOFile());
      }
    });
    List<String> expected = ContainerUtil.map(expectedPaths, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return FileUtil.toSystemDependentName(s);
      }
    });
    assertOrderedEquals(actual, expected);
  }

  private void assertChanges(List<Change> actual, List<GitTestChange> expected) {
    assertEquals(expected.size(), actual.size());
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
        assertEquals(getBeforePath(actualChange), FileUtil.toSystemDependentName(expectedChange.myBeforePath));
        assertEquals(getAfterPath(actualChange), FileUtil.toSystemDependentName(expectedChange.myAfterPath));
        return;
      case NEW:
        assertEquals(getAfterPath(actualChange), FileUtil.toSystemDependentName(expectedChange.myAfterPath));
        return;
      case DELETED:
        assertEquals(getBeforePath(actualChange), FileUtil.toSystemDependentName(expectedChange.myBeforePath));
        return;
      default:
        throw new AssertionError();
    } 
  }

  private String getBeforePath(Change actualChange) {
    return FileUtil.getRelativePath(new File(myProjectPath), actualChange.getBeforeRevision().getFile().getIOFile());
  }

  private String getAfterPath(Change actualChange) {
    return FileUtil.getRelativePath(new File(myProjectPath), actualChange.getAfterRevision().getFile().getIOFile());
  }
  
  private enum GitTestLogRecordInfo {
    HASH,
    COMMIT_TIME,
    AUTHOR_TIME,
    AUTHOR_NAME,
    AUTHOR_EMAIL,
    COMMIT_NAME,
    COMMIT_EMAIL,
    SUBJECT,
    BODY,
    PARENTS,
    CHANGES
  }

  private static class GitTestLogRecord {

    private final Map<GitTestLogRecordInfo, Object> myData;

    GitTestLogRecord(Map<GitTestLogRecordInfo, Object> data) {
      myData = data;
    }

    public String getHash() {
      return (String)myData.get(GitTestLogRecordInfo.HASH);
    }

    public Date getCommitTime() {
      return (Date)myData.get(GitTestLogRecordInfo.COMMIT_TIME);
    }

    public Date getAuthorTime() {
      return (Date)myData.get(GitTestLogRecordInfo.AUTHOR_TIME);
    }

    public String getAuthorName() {
      return (String)myData.get(GitTestLogRecordInfo.AUTHOR_NAME);
    }

    public String getAuthorEmail() {
      return (String)myData.get(GitTestLogRecordInfo.AUTHOR_EMAIL);
    }

    public String getCommitterName() {
      return (String)myData.get(GitTestLogRecordInfo.COMMIT_NAME);
    }

    public String getCommitterEmail() {
      return (String)myData.get(GitTestLogRecordInfo.COMMIT_EMAIL);
    }

    public String getSubject() {
      return (String)myData.get(GitTestLogRecordInfo.SUBJECT);
    }

    public String getBody() {
      return (String)myData.get(GitTestLogRecordInfo.BODY);
    }

    public String[] getParents() {
      return (String[])myData.get(GitTestLogRecordInfo.PARENTS);
    }

    public GitTestChange[] getChanges() {
      return (GitTestChange[])myData.get(GitTestLogRecordInfo.CHANGES);
    }

    String[] shortParents() {
      String[] parents = getParents();
      String[] shortParents = new String[parents.length];
      for (int i = 0 ; i < parents.length; i++) {
        shortParents[i] = parents[i].substring(0, 7);
      }
      return shortParents;
    }

    private String shortParentsAsString() {
      return StringUtil.join(shortParents(), " ");
    }

    private String parentsAsString() {
      return StringUtil.join(getParents(), " ");
    }

    String rawBody() {
      return getSubject() + "\n\n" + getBody();
    }

    List<GitTestChange> changes() {
      return Arrays.asList(getChanges());
    }

    private String changesAsString() {
      StringBuilder sb = new StringBuilder();
      for (GitTestChange change : getChanges()) {
        sb.append(change.toOutputString());
      }
      return sb.toString();
    }

    public List<String> paths() {
      List<String> paths = new ArrayList<String>();
      for (GitTestChange change : getChanges()) {
        switch (change.myType) {
          case MODIFICATION:
          case NEW:
            paths.add(change.myAfterPath);
            break;
          case DELETED:
            paths.add(change.myBeforePath);
            break;
          case MOVED:
            paths.add(change.myBeforePath);
            paths.add(change.myAfterPath);
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
          return getHash();
        case SUBJECT:
          return getSubject();
        case BODY:
          return getBody();
        case RAW_BODY:
          return rawBody();
        case COMMIT_TIME:
          return String.valueOf(getCommitTime().getTime() / 1000);
        case AUTHOR_NAME:
          return getAuthorName();
        case AUTHOR_TIME:
          return String.valueOf(getAuthorTime().getTime() / 1000);
        case AUTHOR_EMAIL:
          return getAuthorEmail();
        case COMMITTER_NAME:
          return getCommitterName();
        case COMMITTER_EMAIL:
          return getCommitterEmail();
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
