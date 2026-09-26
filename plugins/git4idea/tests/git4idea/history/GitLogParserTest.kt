// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.DigestUtil
import git4idea.history.GitLogParser.GitLogOption
import git4idea.history.GitLogParser.GitLogOption.AUTHOR_EMAIL
import git4idea.history.GitLogParser.GitLogOption.AUTHOR_NAME
import git4idea.history.GitLogParser.GitLogOption.AUTHOR_TIME
import git4idea.history.GitLogParser.GitLogOption.BODY
import git4idea.history.GitLogParser.GitLogOption.COMMITTER_EMAIL
import git4idea.history.GitLogParser.GitLogOption.COMMITTER_NAME
import git4idea.history.GitLogParser.GitLogOption.COMMIT_TIME
import git4idea.history.GitLogParser.GitLogOption.HASH
import git4idea.history.GitLogParser.GitLogOption.PARENTS
import git4idea.history.GitLogParser.GitLogOption.RAW_BODY
import git4idea.history.GitLogParser.GitLogOption.REF_NAMES
import git4idea.history.GitLogParser.GitLogOption.SUBJECT
import git4idea.history.GitLogParser.ITEMS_SEPARATOR
import git4idea.history.GitLogParser.NameStatus
import git4idea.history.GitLogParser.NameStatus.NONE
import git4idea.history.GitLogParser.NameStatus.STATUS
import git4idea.history.GitLogParser.RECORD_END
import git4idea.history.GitLogParser.RECORD_START
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Date

@TestApplication
class GitLogParserTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()


  @Throws(VcsException::class)
  @Test
  fun testParseAllWithoutNameStatus() {
    doTestAllRecords(NONE)
  }

  @Throws(VcsException::class)
  @Test
  fun testParseAllWithNameStatus() {
    doTestAllRecords(STATUS)
  }

  @Throws(VcsException::class)
  @Test
  fun testParseOneRecordWithoutNameStatus() = with(context) {
    doTestOneRecord(GitLogParser.createDefaultParser(project, *GIT_LOG_OPTIONS), NONE)
  }

  @Throws(VcsException::class)
  @Test
  fun testParseOneRecordWithNameStatus() = with(context) {
    doTestOneRecord(GitLogParser.createDefaultParser(project, STATUS, *GIT_LOG_OPTIONS), STATUS)
  }

  @Test
  fun test_char_0001_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0001subject")
  }

  @Test
  fun test_double_char_0001_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0001\u0001subject")
  }

  @Test
  fun test_char_0003_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0003subject")
  }

  @Test
  fun test_double_char_0003_in_commit_message() {
    doTestCustomCommitMessage("Commit \u0003\u0003subject")
  }

  @Test
  fun test_both_chars_0001_and_0003_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001of the \u0003# weirdmessage")
  }

  @Test
  fun test_both_double_chars_0001_and_0003_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001\u0001of the \u0003\u0003# weirdmessage")
  }

  @Test
  fun test_char_0001_twice_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001of the \u0001# weird message")
  }

  @Test
  fun test_double_char_0001_twice_in_commit_message() {
    doTestCustomCommitMessage("Subject \u0001\u0001of the \u0001\u0001# weird message")
  }

  @Throws(VcsException::class)
  @Test
  fun test_old_refs_format() {
    doTestAllRecords(NONE)
  }

  @Throws(VcsException::class)
  @Test
  fun test_new_refs_format() {
    doTestAllRecords(STATUS, true)
  }

  @Throws(VcsException::class)
  @Test
  fun test_files_with_spaces(): Unit = with(context) {
    val parser = GitLogParser.createDefaultParser(project, STATUS, *GIT_LOG_OPTIONS)
    val expectedRecord = createTestRecord(changes = listOf(modified("file "), modified(" ")))
    val actualRecord = parser.parseOneRecord(expectedRecord.prepareOutputLine(STATUS))!!
    assertRecord(actualRecord, expectedRecord, STATUS)
  }

  @Throws(VcsException::class)
  private fun doTestAllRecords(nameStatusOption: NameStatus, newRefsFormat: Boolean = false): Unit = with(context) {
    val expectedRecords = generateRecords(newRefsFormat)

    val parser = GitLogParser.createDefaultParser(project, nameStatusOption, *GIT_LOG_OPTIONS)

    val output = expectedRecords.joinToString("\n") { it.prepareOutputLine(nameStatusOption) }
    val actualRecords = parser.parse(output)
    assertThat(expectedRecords.size).isEqualTo(actualRecords.size)
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

  private fun doTestCustomCommitMessage(subject: String): Unit = with(context) {
    val record = generateRecordWithSubject(subject)

    val parser = GitLogParser.createDefaultParser(project, STATUS, *GIT_LOG_OPTIONS)
    val s = record.prepareOutputLine(NONE)
    val records = parser.parse(s)
    assertThat(records.size).describedAs("Incorrect amount of actual records: " + StringUtil.join(records, "\n")).isEqualTo(1)
    assertThat(records[0].subject).describedAs("Commit subject is incorrect").isEqualTo(subject)
  }

  @Throws(VcsException::class)
  private fun assertRecord(actual: GitLogRecord, expected: GitTestLogRecord, option: NameStatus): Unit = with(context) {
    assertThat(actual.hash).isEqualTo(expected.hash)

    assertThat(actual.committerName).isEqualTo(expected.committerName)
    assertThat(actual.committerEmail).isEqualTo(expected.committerEmail)
    assertThat(actual.date).isEqualTo(expected.commitTime)

    assertThat(actual.authorName).isEqualTo(expected.authorName)
    assertThat(actual.authorEmail).isEqualTo(expected.authorEmail)
    assertThat(actual.authorTimeStamp).isEqualTo(expected.authorTime.time)

    assertThat(actual.subject).isEqualTo(expected.subject)
    assertThat(actual.body).isEqualTo(expected.body)
    assertThat(actual.rawBody).isEqualTo(expected.rawBody)

    assertThat(actual.parentsHashes).containsExactlyInAnyOrder(*expected.parents)
    assertThat(actual.refs).containsExactlyInAnyOrderElementsOf(expected.refs)

    if (option == STATUS) {
      if (actual is GitLogFullRecord) {
        val actualPaths = actual.getFilePaths(projectRoot).map { FileUtil.getRelativePath(File(projectPath), it.ioFile) }
        val expectedPaths = expected.paths().map { FileUtil.toSystemDependentName(it) }
        assertThat(actualPaths).containsExactlyElementsOf(expectedPaths)

        val actualChanges = actual.parseChanges(project, projectRoot)
        val expectedChanges = expected.changes
        assertThat(actualChanges.size).isEqualTo(expectedChanges.size)
        for (i in actualChanges.indices) {
          assertChange(actualChanges[i], expectedChanges[i])
        }
      }
      else {
        Assertions.fail("$actual is not a GitLogFullRecord")
      }
    }
  }

  private fun assertChange(actualChange: Change, expectedChange: GitTestLogRecord.GitTestChange) {
    assertThat(expectedChange.type).isEqualTo(actualChange.type)
    when (actualChange.type) {
      Change.Type.MODIFICATION, Change.Type.MOVED -> {
        assertThat(FileUtil.toSystemDependentName(expectedChange.beforePath!!)).isEqualTo(getBeforePath(actualChange))
        assertThat(FileUtil.toSystemDependentName(expectedChange.afterPath!!)).isEqualTo(getAfterPath(actualChange))
        return
      }
      Change.Type.NEW -> {
        assertThat(FileUtil.toSystemDependentName(expectedChange.afterPath!!)).isEqualTo(getAfterPath(actualChange))
        return
      }
      Change.Type.DELETED -> {
        assertThat(FileUtil.toSystemDependentName(expectedChange.beforePath!!)).isEqualTo(getBeforePath(actualChange))
        return
      }
    }
  }

  private fun getBeforePath(actualChange: Change): String? = with(context) {
    return FileUtil.getRelativePath(File(projectPath), actualChange.beforeRevision!!.file.ioFile)
  }

  private fun getAfterPath(actualChange: Change): String? = with(context) {
    return FileUtil.getRelativePath(File(projectPath), actualChange.afterRevision!!.file.ioFile)
  }

}

private class GitTestLogRecord(
  private val data: Map<GitLogOption, Any>,
  val changes: List<GitTestChange> = emptyList(),
  private val newRefsFormat: Boolean = false,
) {
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
    get() = (data[PARENTS] as? Array<*>)?.map { it as String }?.toTypedArray() ?: emptyArray()

  val refs: Collection<String>
    get() = (data[REF_NAMES] as? List<*>)?.map { it as String } ?: emptyList()

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
      }
    }
    return paths
  }

  fun prepareOutputLine(nameStatusOption: NameStatus): String {
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

  class GitTestChange(
    val type: Change.Type,
    val beforePath: String?,
    val afterPath: String?,
  ) {

    private fun toOutputString(type: Change.Type): String {
      when (type) {
        Change.Type.MOVED -> return "R100"
        Change.Type.MODIFICATION -> return "M"
        Change.Type.DELETED -> return "D"
        Change.Type.NEW -> return "A"
      }
    }

    fun toOutputString(): String {
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

private fun createTestRecord(
  vararg parameters: Pair<GitLogOption, Any>,
  changes: List<GitTestLogRecord.GitTestChange> = emptyList(),
  newRefsFormat: Boolean = false,
): GitTestLogRecord {
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
  data[HASH] = DigestUtil.sha1Hex(data.toString())
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
                               Pair(REF_NAMES, listOf("HEAD", "refs/heads/master", "refs/heads/(ref1)", "refs/heads/ref2")),
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
                               Pair(REF_NAMES, listOf("refs/heads/sly->name", "refs/remotes/origin/master", "refs/tags/v1.0")),
                               changes = listOf(modified("src/CClass.java")),
                               newRefsFormat = newRefsFormat))

  return records
}
