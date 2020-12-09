// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.testFramework.UsefulTestCase
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

  @Throws(VcsException::class)
  fun testParseAllWithoutNameStatus() {
    doTestAllRecords(NONE)
  }

  @Throws(VcsException::class)
  fun testParseAllWithNameStatus() {
    doTestAllRecords(STATUS)
  }

  @Throws(VcsException::class)
  fun testParseOneRecordWithoutNameStatus() {
    doTestOneRecord(GitLogParser.createDefaultParser(myProject, *GIT_LOG_OPTIONS), NONE)
  }

  @Throws(VcsException::class)
  fun testParseOneRecordWithNameStatus() {
    doTestOneRecord(GitLogParser.createDefaultParser(myProject, STATUS, *GIT_LOG_OPTIONS), STATUS)
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

  @Throws(VcsException::class)
  fun test_files_with_spaces() {
    val parser = GitLogParser.createDefaultParser(myProject, STATUS, *GIT_LOG_OPTIONS)
    val expectedRecord = createTestRecord(changes = listOf(modified("file "), modified(" ")))
    val actualRecord = parser.parseOneRecord(expectedRecord.prepareOutputLine(STATUS))!!
    assertRecord(actualRecord, expectedRecord, STATUS)
  }

  @Throws(VcsException::class)
  private fun doTestAllRecords(nameStatusOption: NameStatus, newRefsFormat: Boolean = false) {
    val expectedRecords = generateRecords(newRefsFormat)

    val parser = GitLogParser.createDefaultParser(myProject, nameStatusOption, *GIT_LOG_OPTIONS)

    val output = expectedRecords.joinToString("\n") { it.prepareOutputLine(nameStatusOption) }
    val actualRecords = parser.parse(output)
    TestCase.assertEquals(actualRecords.size, expectedRecords.size)
    for (i in actualRecords.indices) {
      assertRecord(actualRecords[i], expectedRecords[i], nameStatusOption)
    }
  }

  @Throws(VcsException::class)
  private fun <R : GitLogRecord> doTestOneRecord(parser: GitLogParser<R>, option: NameStatus) {
    val expectedRecord = generateRecordWithSubject("Subject")
    val s = expectedRecord.prepareOutputLine(option)
    val actualRecord = parser.parseOneRecord(s)!!
    assertRecord(actualRecord, expectedRecord, option)
  }

  private fun doTestCustomCommitMessage(subject: String) {
    val record = generateRecordWithSubject(subject)

    val parser = GitLogParser.createDefaultParser(myProject, STATUS, *GIT_LOG_OPTIONS)
    val s = record.prepareOutputLine(NONE)
    val records = parser.parse(s)
    TestCase.assertEquals("Incorrect amount of actual records: " + StringUtil.join(records, "\n"), 1, records.size)
    TestCase.assertEquals("Commit subject is incorrect", subject, records[0].subject)
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

    TestCase.assertEquals(expected.subject, actual.subject)
    TestCase.assertEquals(expected.body, actual.body)
    TestCase.assertEquals(expected.rawBody, actual.rawBody)

    UsefulTestCase.assertSameElements(actual.parentsHashes, *expected.parents)
    UsefulTestCase.assertSameElements(actual.refs, expected.refs)

    if (option == STATUS) {
      if (actual is GitLogFullRecord) {
        val actualPaths = actual.getFilePaths(projectRoot).map { FileUtil.getRelativePath(File(projectPath), it.ioFile) }
        val expectedPaths = expected.paths().map { FileUtil.toSystemDependentName(it) }
        UsefulTestCase.assertOrderedEquals(actualPaths, expectedPaths)

        val actualChanges = actual.parseChanges(myProject, projectRoot)
        val expectedChanges = expected.changes
        TestCase.assertEquals(expectedChanges.size, actualChanges.size)
        for (i in actualChanges.indices) {
          assertChange(actualChanges[i], expectedChanges[i])
        }
      } else {
        TestCase.fail("$actual is not a GitLogFullRecord")
      }
    }
  }

  private fun assertChange(actualChange: Change, expectedChange: GitTestLogRecord.GitTestChange) {
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

}

private class GitTestLogRecord internal constructor(private val data: Map<GitLogOption, Any>,
                                                    val changes: List<GitTestChange> = emptyList(),
                                                    private val newRefsFormat: Boolean = false) {
  val hash: String
    get() = data[HASH] as String

  val commitTime: Date
    get() = data[COMMIT_TIME] as Date

  val authorTime: Date
    get() = data[AUTHOR_TIME] as Date

  val authorName: String
    get() = data[AUTHOR_NAME] as String

  val authorEmail: String
    get() = data[AUTHOR_EMAIL] as String

  val committerName: String
    get() = data[COMMITTER_NAME] as String

  val committerEmail: String
    get() = data[COMMITTER_EMAIL] as String

  val subject: String
    get() = data[SUBJECT] as String

  val body: String
    get() = data[BODY] as String

  val parents: Array<String>
    get() = data[PARENTS] as Array<String>? ?: emptyArray()

  val refs: Collection<String>
    get() = data[REF_NAMES] as List<String>? ?: emptyList()

  val refsForOutput: String
    get() {
      var refs = refs
      if (refs.isEmpty()) {
        return ""
      }
      if (newRefsFormat) {
        val newRefs = mutableListOf<String>()
        var headRefMet = false
        for (ref in refs) {
          when {
            ref == "HEAD" -> headRefMet = true
            headRefMet -> {
              newRefs.add("HEAD -> $ref")
              headRefMet = false
            }
            else -> newRefs.add(ref)
          }
        }
        refs = newRefs
      }
      return "(" + StringUtil.join(refs, ", ") + ")"
    }

  val rawBody: String
    get() = subject + "\n\n" + body

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
      sb.append("\n\n").append(changes.joinToString("") { it.toOutputString() })
    }

    return sb.toString()
  }

  private fun optionToValue(option: GitLogOption): String {
    when (option) {
      RAW_BODY -> return rawBody
      COMMIT_TIME -> return (commitTime.time / 1000).toString()
      AUTHOR_TIME -> return (authorTime.time / 1000).toString()
      PARENTS -> return parents.joinToString(" ")
      REF_NAMES -> return refsForOutput
      else -> return data[option] as String
    }
  }

  internal class GitTestChange internal constructor(internal val type: Change.Type,
                                                    internal val beforePath: String?,
                                                    internal val afterPath: String?) {

    private fun toOutputString(type: Change.Type): String {
      when (type) {
        Change.Type.MOVED -> return "R100"
        Change.Type.MODIFICATION -> return "M"
        Change.Type.DELETED -> return "D"
        Change.Type.NEW -> return "A"
        else -> throw AssertionError()
      }
    }

    internal fun toOutputString(): String {
      val sb = StringBuilder()
      sb.append(toOutputString(type)).append("\t")
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

private fun added(path: String): GitTestLogRecord.GitTestChange {
  return GitTestLogRecord.GitTestChange(Change.Type.NEW, null, path)
}

private fun deleted(path: String): GitTestLogRecord.GitTestChange {
  return GitTestLogRecord.GitTestChange(Change.Type.DELETED, path, null)
}

private fun modified(path: String): GitTestLogRecord.GitTestChange {
  return GitTestLogRecord.GitTestChange(Change.Type.MODIFICATION, path, path)
}

private fun moved(before: String, after: String): GitTestLogRecord.GitTestChange {
  return GitTestLogRecord.GitTestChange(Change.Type.MOVED, before, after)
}

private val GIT_LOG_OPTIONS = arrayOf(HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL,
                                      SUBJECT, BODY, PARENTS, RAW_BODY, REF_NAMES)

private fun createTestRecord(vararg parameters: Pair<GitLogOption, Any>,
                             changes: List<GitTestLogRecord.GitTestChange> = emptyList(),
                             newRefsFormat: Boolean = false): GitTestLogRecord {
  val data = mutableMapOf<GitLogOption, Any>(
                          Pair(SUBJECT, "Subject"),
                          Pair(BODY, "Body"),
                          Pair(AUTHOR_TIME, Date(1317027817L * 1000)),
                          Pair(AUTHOR_NAME, "John Doe"),
                          Pair(AUTHOR_EMAIL, "John.Doe@example.com"),
                          Pair(COMMIT_TIME, Date(1315471452L * 1000)),
                          Pair(COMMITTER_NAME, "John Doe"),
                          Pair(COMMITTER_EMAIL, "John.Doe@example.com"))
  parameters.associateTo(data) { it }
  data[HASH] = DigestUtils.sha1Hex(data.toString())
  return GitTestLogRecord(data, changes, newRefsFormat)
}

private fun generateRecordWithSubject(subject: String): GitTestLogRecord {
  return createTestRecord(Pair(SUBJECT, subject),
                          Pair(BODY, "Small description"),
                          Pair(PARENTS, arrayOf("2c815939f45fbcfda9583f84b14fe9d393ada790")),
                          changes = listOf(modified("src/CClass.java"),
                                           added("src/OtherClass.java"),
                                           deleted("src/OldClass.java")))
}

private fun generateRecords(newRefsFormat: Boolean): MutableList<GitTestLogRecord> {
  val records = mutableListOf<GitTestLogRecord>()
  records.add(createTestRecord(Pair(COMMITTER_NAME, "Bob Smith"),
                               Pair(COMMITTER_EMAIL, "Bob@site.com"),
                               Pair(SUBJECT, "Commit message"),
                               Pair(BODY, "Description goes here\n" +
                                          "\n" + // empty line

                                          "Then comes a long long description.\n" +
                                          "Probably multilined."),
                               Pair(REF_NAMES, Arrays.asList("HEAD", "refs/heads/master", "refs/heads/(ref1)", "refs/heads/ref2")),
                               changes = listOf(moved("file2", "file3"),
                                                added("readme.txt"),
                                                modified("src/CClass.java"),
                                                deleted("src/ChildAClass.java")),
                               newRefsFormat = newRefsFormat))

  records.add(createTestRecord(Pair(SUBJECT, "Commit message"),
                               Pair(BODY, "Small description"),
                               Pair(PARENTS, arrayOf(records[0].hash)),
                               changes = listOf(modified("src/CClass.java")),
                               newRefsFormat = newRefsFormat))

  records.add(createTestRecord(Pair(SUBJECT, "Commit message"),
                               Pair(BODY, "Small description"),
                               Pair(PARENTS, arrayOf(records[0].hash, records[1].hash)),
                               Pair(REF_NAMES, Arrays.asList("refs/heads/sly->name", "refs/remotes/origin/master",
                                                             "refs/tags/v1.0")),
                               changes = listOf(modified("src/CClass.java")),
                               newRefsFormat = newRefsFormat))

  return records
}