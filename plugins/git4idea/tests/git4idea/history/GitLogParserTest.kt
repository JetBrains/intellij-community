// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import git4idea.GitUtil
import git4idea.history.GitLogParser.*
import git4idea.history.GitLogParser.GitLogOption.*
import git4idea.history.GitLogParser.NameStatus.NONE
import git4idea.history.GitLogParser.NameStatus.STATUS
import git4idea.test.GitPlatformTest
import junit.framework.TestCase
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.util.*

class GitLogParserTest : GitPlatformTest() {
  private var root: VirtualFile? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    root = projectRoot
  }

  @Throws(VcsException::class)
  fun testParseAllWithoutNameStatus() {
    doTestAllRecords(NONE)
  }

  @Throws(VcsException::class)
  fun testParseAllWithNameStatus() {
    doTestAllRecords(STATUS)
  }

  @Throws(VcsException::class)
  private fun doTestAllRecords(nameStatusOption: NameStatus, newRefsFormat: Boolean = false) {
    val expectedRecords = generateRecords(newRefsFormat)

    val option: NameStatus
    when (nameStatusOption) {
      NONE -> option = NONE
      STATUS -> option = STATUS
      else -> throw AssertionError()
    }

    val parser = GitLogParser(myProject, option, *GIT_LOG_OPTIONS)

    val output = expectedRecords.joinToString("\n") { it.prepareOutputLine(nameStatusOption) }
    val actualRecords = parser.parse(output)
    assertAllRecords(actualRecords, expectedRecords, nameStatusOption)
  }

  @Throws(VcsException::class)
  fun testParseOneRecordWithoutNameStatus() {
    doTestOneRecord(GitLogParser(myProject, *GIT_LOG_OPTIONS), NONE)
  }

  @Throws(VcsException::class)
  fun testParseOneRecordWithNameStatus() {
    doTestOneRecord(GitLogParser(myProject, STATUS, *GIT_LOG_OPTIONS), STATUS)
  }

  fun test_char_0001_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0001subject")
  }

  fun test_double_char_0001_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0001\u0001subject")
  }

  fun test_char_0003_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0003subject")
  }

  fun test_double_char_0003_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0003\u0003subject")
  }

  fun test_both_chars_0001_and_0003_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001of the \u0003# weirdmessage")
  }

  fun test_both_double_chars_0001_and_0003_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001\u0001of the \u0003\u0003# weirdmessage")
  }

  fun test_char_0001_twice_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001of the \u0001# weird message")
  }

  fun test_double_char_0001_twice_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001\u0001of the \u0001\u0001# weird message")
  }

  @Throws(VcsException::class)
  fun test_old_refs_format() {
    doTestAllRecords(NONE)
  }

  @Throws(VcsException::class)
  fun test_new_refs_format() {
    doTestAllRecords(STATUS, true)
  }

  private fun doTestCustomCommitMessage(subject: String) {
    val record = generateRecordWithSubject(subject)

    val parser = GitLogParser(myProject, STATUS, *GIT_LOG_OPTIONS)
    val s = record.prepareOutputLine(NONE)
    val records = parser.parse(s)
    TestCase.assertEquals("Incorrect amount of actual records: " + StringUtil.join(records, "\n"), 1, records.size)
    TestCase.assertEquals("Commit subject is incorrect", subject, records[0].subject)
  }

  @Throws(VcsException::class)
  private fun doTestOneRecord(parser: GitLogParser, option: NameStatus) {
    val expectedRecord = generateRecordWithSubject("Subject")
    val s = expectedRecord.prepareOutputLine(option)
    val actualRecord = parser.parseOneRecord(s)!!
    assertRecord(actualRecord, expectedRecord, option)
  }

  @Throws(VcsException::class)
  private fun assertAllRecords(actualRecords: List<GitLogRecord>,
                               expectedRecords: List<GitTestLogRecord>,
                               nameStatusOption: NameStatus) {
    TestCase.assertEquals(actualRecords.size, expectedRecords.size)
    for (i in actualRecords.indices) {
      assertRecord(actualRecords[i], expectedRecords[i], nameStatusOption)
    }
  }

  @Throws(VcsException::class)
  private fun assertRecord(actual: GitLogRecord, expected: GitTestLogRecord, option: NameStatus) {
    TestCase.assertEquals(expected.hash, actual.hash)

    TestCase.assertEquals(expected.committerName, actual.committerName)
    TestCase.assertEquals(expected.committerEmail, actual.committerEmail)
    TestCase.assertEquals(expected.commitTime, actual.date)

    TestCase.assertEquals(expected.authorName, actual.authorName)
    TestCase.assertEquals(expected.authorEmail, actual.authorEmail)
    TestCase.assertEquals(expected.authorTime.time, actual.authorTimeStamp)

    val expectedAuthorAndCommitter = GitUtil.adjustAuthorName(
      String.format("%s <%s>", expected.authorName, expected.authorEmail),
      String.format("%s <%s>", expected.committerName, expected.committerEmail))
    TestCase.assertEquals(expectedAuthorAndCommitter, getAuthorAndCommitter(actual))


    TestCase.assertEquals(expected.subject, actual.subject)
    TestCase.assertEquals(expected.body, actual.body)
    TestCase.assertEquals(expected.rawBody(), actual.rawBody)

    UsefulTestCase.assertSameElements(actual.parentsHashes, *expected.parents)

    UsefulTestCase.assertSameElements(actual.refs, expected.refs)

    if (option == STATUS) {
      assertPaths(actual.getFilePaths(root!!), expected.paths())
      assertChanges(actual.parseChanges(myProject, root!!), expected.changes())
    }
  }

  private fun getAuthorAndCommitter(actual: GitLogRecord): String {
    val author = String.format("%s <%s>", actual.authorName, actual.authorEmail)
    val committer = String.format("%s <%s>", actual.committerName, actual.committerEmail)
    return GitUtil.adjustAuthorName(author, committer)
  }

  private fun assertPaths(actualPaths: List<FilePath>, expectedPaths: List<String>) {
    val actual = ContainerUtil.map<FilePath, String>(actualPaths) { path -> FileUtil.getRelativePath(File(projectPath), path.ioFile) }
    val expected = ContainerUtil.map(expectedPaths) { s -> FileUtil.toSystemDependentName(s) }
    UsefulTestCase.assertOrderedEquals(actual, expected)
  }

  private fun assertChanges(actual: List<Change>, expected: List<GitTestChange>) {
    TestCase.assertEquals(expected.size, actual.size)
    for (i in actual.indices) {
      val actualChange = actual[i]
      val expectedChange = expected[i]
      assertChange(actualChange, expectedChange)
    }
  }

  private fun assertChange(actualChange: Change, expectedChange: GitTestChange) {
    TestCase.assertEquals(actualChange.type, expectedChange.type)
    when (actualChange.type) {
      Change.Type.MODIFICATION, Change.Type.MOVED -> {
        TestCase.assertEquals(getBeforePath(actualChange), FileUtil.toSystemDependentName(expectedChange.beforePath!!))
        TestCase.assertEquals(getAfterPath(actualChange), FileUtil.toSystemDependentName(expectedChange.afterPath!!))
        return
      }
      Change.Type.NEW -> {
        TestCase.assertEquals(getAfterPath(actualChange), FileUtil.toSystemDependentName(expectedChange.afterPath!!))
        return
      }
      Change.Type.DELETED -> {
        TestCase.assertEquals(getBeforePath(actualChange), FileUtil.toSystemDependentName(expectedChange.beforePath!!))
        return
      }
      else -> throw AssertionError()
    }
  }

  private fun getBeforePath(actualChange: Change): String? {
    return FileUtil.getRelativePath(File(projectPath), actualChange.beforeRevision!!.file.ioFile)
  }

  private fun getAfterPath(actualChange: Change): String? {
    return FileUtil.getRelativePath(File(projectPath), actualChange.afterRevision!!.file.ioFile)
  }

  internal enum class GitTestLogRecordInfo {
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
    REFS,
    CHANGES
  }

  internal class GitTestLogRecord internal constructor(private val data: Map<GitTestLogRecordInfo, Any>,
                                                       private val newRefsFormat: Boolean = false) {

    val hash: String
      get() = data[GitTestLogRecordInfo.HASH] as String

    val commitTime: Date
      get() = data[GitTestLogRecordInfo.COMMIT_TIME] as Date

    val authorTime: Date
      get() = data[GitTestLogRecordInfo.AUTHOR_TIME] as Date

    val authorName: String
      get() = data[GitTestLogRecordInfo.AUTHOR_NAME] as String

    val authorEmail: String
      get() = data[GitTestLogRecordInfo.AUTHOR_EMAIL] as String

    val committerName: String
      get() = data[GitTestLogRecordInfo.COMMIT_NAME] as String

    val committerEmail: String
      get() = data[GitTestLogRecordInfo.COMMIT_EMAIL] as String

    val subject: String
      get() = data[GitTestLogRecordInfo.SUBJECT] as String

    val body: String
      get() = data[GitTestLogRecordInfo.BODY] as String

    val parents: Array<String>
      get() = data[GitTestLogRecordInfo.PARENTS] as Array<String>? ?: emptyArray()

    val refs: Collection<String>
      get() = data[GitTestLogRecordInfo.REFS] as List<String>? ?: emptyList()

    val refsForOutput: String
      get() {
        var refs = refs
        if (refs.isEmpty()) {
          return ""
        }
        if (newRefsFormat) {
          val newRefs = ContainerUtil.newArrayList<String>()
          var headRefMet = false
          for (ref in refs) {
            if (ref == "HEAD") {
              headRefMet = true
            }
            else if (headRefMet) {
              newRefs.add("HEAD -> $ref")
              headRefMet = false
            }
            else {
              newRefs.add(ref)
            }
          }
          refs = newRefs
        }
        return "(" + StringUtil.join(refs, ", ") + ")"
      }

    val changes: Array<GitTestChange>
      get() = data[GitTestLogRecordInfo.CHANGES] as Array<GitTestChange>

    private fun parentsAsString(): String {
      return parents.joinToString(" ")
    }

    internal fun rawBody(): String {
      return subject + "\n\n" + body
    }

    internal fun changes(): List<GitTestChange> {
      return Arrays.asList(*changes)
    }

    private fun changesAsString(): String {
      val sb = StringBuilder()
      for (change in changes) {
        sb.append(change.toOutputString())
      }
      return sb.toString()
    }

    fun paths(): List<String> {
      val paths = ArrayList<String>()
      for (change in changes) {
        when (change.type) {
          Change.Type.MODIFICATION, Change.Type.NEW -> paths.add(change.afterPath!!)
          Change.Type.DELETED -> paths.add(change.beforePath!!)
          Change.Type.MOVED -> {
            paths.add(change.beforePath!!)
            paths.add(change.afterPath!!)
          }
          else -> throw AssertionError()
        }
      }
      return paths
    }

    internal fun prepareOutputLine(nameStatusOption: NameStatus): String {
      val sb = StringBuilder(RECORD_START)
      sb.append(GIT_LOG_OPTIONS.joinToString(ITEMS_SEPARATOR) { optionToValue(it) })
      sb.append(RECORD_END)

      if (nameStatusOption == STATUS) {
        sb.append("\n\n").append(changesAsString())
      }

      return sb.toString()
    }

    private fun optionToValue(option: GitLogOption): String {
      when (option) {
        HASH -> return hash
        SUBJECT -> return subject
        BODY -> return body
        RAW_BODY -> return rawBody()
        COMMIT_TIME -> return (commitTime.time / 1000).toString()
        AUTHOR_NAME -> return authorName
        AUTHOR_TIME -> return (authorTime.time / 1000).toString()
        AUTHOR_EMAIL -> return authorEmail
        COMMITTER_NAME -> return committerName
        COMMITTER_EMAIL -> return committerEmail
        PARENTS -> return parentsAsString()
        REF_NAMES -> return refsForOutput
        SHORT_REF_LOG_SELECTOR -> {
        }
        TREE -> {
        }
      }
      throw AssertionError()
    }
  }

  internal class GitTestChange internal constructor(internal val type: Change.Type,
                                                    internal val beforePath: String?,
                                                    internal val afterPath: String?) {

    internal fun toOutputString(): String {
      when (type) {
        Change.Type.MOVED -> return outputString("R100", beforePath, afterPath)
        Change.Type.MODIFICATION -> return outputString("M", beforePath, null)
        Change.Type.DELETED -> return outputString("D", beforePath, null)
        Change.Type.NEW -> return outputString("A", afterPath, null)
        else -> throw AssertionError()
      }
    }

    companion object {

      internal fun added(path: String): GitTestChange {
        return GitTestChange(Change.Type.NEW, null, path)
      }

      internal fun deleted(path: String): GitTestChange {
        return GitTestChange(Change.Type.DELETED, path, null)
      }

      internal fun modified(path: String): GitTestChange {
        return GitTestChange(Change.Type.MODIFICATION, path, path)
      }

      internal fun moved(before: String, after: String): GitTestChange {
        return GitTestChange(Change.Type.MOVED, before, after)
      }

      private fun outputString(type: String, beforePath: String?, afterPath: String?): String {
        val sb = StringBuilder()
        sb.append(type).append("\t")
        if (beforePath != null) {
          sb.append(beforePath).append("\t")
        }
        if (afterPath != null) {
          sb.append(afterPath).append("\t")
        }
        sb.append("\n")
        return sb.toString()
      }
    }
  }

  companion object {
    internal val GIT_LOG_OPTIONS = arrayOf(HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL,
                                           SUBJECT, BODY,
                                           PARENTS, PARENTS, RAW_BODY, REF_NAMES)
  }
}

private fun createTestRecord(vararg parameters: Pair<GitLogParserTest.GitTestLogRecordInfo, Any>,
                             newRefsFormat: Boolean = false): GitLogParserTest.GitTestLogRecord {
  val data = mutableMapOf(Pair(GitLogParserTest.GitTestLogRecordInfo.AUTHOR_TIME, Date(1317027817L * 1000)),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.AUTHOR_NAME, "John Doe"),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.AUTHOR_EMAIL, "John.Doe@example.com"),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.COMMIT_TIME, Date(1315471452L * 1000)),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.COMMIT_NAME, "John Doe"),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.COMMIT_EMAIL, "John.Doe@example.com"))
  parameters.associateTo(data) { it }
  data[GitLogParserTest.GitTestLogRecordInfo.HASH] = DigestUtils.sha1Hex(data.toString())
  return GitLogParserTest.GitTestLogRecord(data, newRefsFormat)
}

internal fun generateRecordWithSubject(subject: String): GitLogParserTest.GitTestLogRecord {
  return createTestRecord(Pair(GitLogParserTest.GitTestLogRecordInfo.SUBJECT, subject),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.BODY, "Small description"),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.PARENTS, arrayOf("2c815939f45fbcfda9583f84b14fe9d393ada790")),
                          Pair(GitLogParserTest.GitTestLogRecordInfo.CHANGES,
                               arrayOf(GitLogParserTest.GitTestChange.modified("src/CClass.java"),
                                       GitLogParserTest.GitTestChange.added("src/OtherClass.java"),
                                       GitLogParserTest.GitTestChange.deleted("src/OldClass.java"))))
}

internal fun generateRecords(newRefsFormat: Boolean): MutableList<GitLogParserTest.GitTestLogRecord> {
  val records = mutableListOf<GitLogParserTest.GitTestLogRecord>()
  records.add(createTestRecord(Pair(GitLogParserTest.GitTestLogRecordInfo.COMMIT_NAME, "Bob Smith"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.COMMIT_EMAIL, "Bob@site.com"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.SUBJECT, "Commit message"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.BODY, "Description goes here\n" +
                                                                                "\n" + // empty line

                                                                                "Then comes a long long description.\n" +
                                                                                "Probably multilined."),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.CHANGES,
                                    arrayOf(GitLogParserTest.GitTestChange.moved("file2", "file3"),
                                            GitLogParserTest.GitTestChange.added("readme.txt"),
                                            GitLogParserTest.GitTestChange.modified("src/CClass.java"),
                                            GitLogParserTest.GitTestChange.deleted("src/ChildAClass.java"))),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.REFS,
                                    Arrays.asList("HEAD", "refs/heads/master")),
                               newRefsFormat = newRefsFormat))

  records.add(createTestRecord(Pair(GitLogParserTest.GitTestLogRecordInfo.SUBJECT, "Commit message"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.BODY, "Small description"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.PARENTS, arrayOf(records[0].hash)),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.CHANGES,
                                    arrayOf(GitLogParserTest.GitTestChange.modified("src/CClass.java"))),
                               newRefsFormat = newRefsFormat))

  records.add(createTestRecord(Pair(GitLogParserTest.GitTestLogRecordInfo.SUBJECT, "Commit message"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.BODY, "Small description"),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.PARENTS,
                                    arrayOf(records[0].hash, records[1].hash)),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.CHANGES,
                                    arrayOf(GitLogParserTest.GitTestChange.modified("src/CClass.java"))),
                               Pair(GitLogParserTest.GitTestLogRecordInfo.REFS,
                                    Arrays.asList("refs/heads/sly->name", "refs/remotes/origin/master",
                                                  "refs/tags/v1.0")),
                               newRefsFormat = newRefsFormat))

  return records
}