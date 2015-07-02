package com.intellij.options

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.options.BaseSchemeProcessor
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.options.SchemeManagerImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import gnu.trove.THashMap
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsMapContaining.hasKey
import org.jdom.Element
import org.jetbrains.testFramework.FixtureRule
import org.jetbrains.testFramework.TemporaryDirectory
import org.jetbrains.testFramework.exists
import org.junit.Rule
import org.junit.Test
import java.io.File

val FILE_SPEC = "REMOTE"

class SchemeManagerTest {
  private val fixtureManager = FixtureRule()
  public Rule fun getFixtureManager(): FixtureRule = fixtureManager

  private val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  private var localBaseDir: File? = null
  private var remoteBaseDir: File? = null

  private fun getTestDataPath() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/options"

  public Test fun testLoadSchemes() {
    doLoadSaveTest("options1", "1->first;2->second")
  }

  public Test fun testLoadSimpleSchemes() {
    doLoadSaveTest("options", "1->1")
  }

  public Test fun testDeleteScheme() {
    val manager = createAndLoad("options1")
    manager.removeScheme(TestScheme("first"))
    manager.save()

    checkSchemes("2->second")
  }

  public Test fun testRenameScheme() {
    val manager = createAndLoad("options1")

    val scheme = manager.findSchemeByName("first")
    assertThat(scheme, notNullValue())
    assert(scheme != null)
    scheme!!.setName("renamed")
    manager.save()

    checkSchemes("2->second;renamed->renamed")
  }

  public Test fun testRenameScheme2() {
    val manager = createAndLoad("options1")

    val first = manager.findSchemeByName("first")
    assertThat(first, notNullValue())
    assert(first != null)
    first!!.setName("2")
    val second = manager.findSchemeByName("second")
    assertThat(second, notNullValue())
    assert(second != null)
    second!!.setName("1")
    manager.save()

    checkSchemes("1->1;2->2")
  }

  public Test fun testDeleteRenamedScheme() {
    val manager = createAndLoad("options1")

    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme, notNullValue())
    assert(firstScheme != null)
    firstScheme!!.setName("first_renamed")
    manager.save()

    checkSchemes(File(remoteBaseDir, "REMOTE"), "first_renamed->first_renamed;2->second", true)
    checkSchemes(localBaseDir!!, "", false)

    firstScheme.setName("first_renamed2")
    manager.removeScheme(firstScheme)
    manager.save()

    checkSchemes(File(remoteBaseDir, "REMOTE"), "2->second", true)
    checkSchemes(localBaseDir!!, "", false)
  }

  public Test fun testDeleteAndCreateSchemeWithTheSameName() {
    val manager = createAndLoad("options1")
    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme, notNullValue())

    manager.removeScheme(firstScheme!!)
    manager.addScheme(TestScheme("first"))
    manager.save()
    checkSchemes("2->second;first->first")
  }

  public Test fun testGenerateUniqueSchemeName() {
    val manager = createAndLoad("options1")
    val scheme = TestScheme("first")
    manager.addNewScheme(scheme, false)

    assertThat("first2", equalTo(scheme.getName()))
  }

  public Test fun `different extensions`() {
    val dir = tempDirManager.newDirectory()
    FileUtil.copyDir(File("${getTestDataPath()}/different-extensions"), dir, false)
    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, object: TestSchemesProcessor() {
      override fun isUpgradeNeeded() = true

      override fun getSchemeExtension() = ".icls"
    }, RoamingType.PER_USER, null, dir)
    schemesManager.loadSchemes()
    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    val scheme = schemes.get(0)
    assertThat(scheme.data, equalTo("true"))

    assertThat(File(dir, "1.icls"), exists())
    assertThat(File(dir, "1.xml"), exists())

    scheme.data = "newTrue"
    save(schemesManager)

    assertThat(File(dir, "1.icls"), exists())
    assertThat(File(dir, "1.xml"), not(exists()))
  }

  public Test fun setSchemes() {
    val dir = tempDirManager.newDirectory()
    val schemesManager = createSchemeManager(dir)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes().isEmpty(), equalTo(true))

    val scheme = TestScheme("s1")
    schemesManager.setSchemes(listOf(scheme))

    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0), sameInstance(scheme))

    assertThat(File(dir, "s1.xml"), not(exists()))

    scheme.data = "newTrue"
    save(schemesManager)

    assertThat(File(dir, "s1.xml"), exists())

    schemesManager.setSchemes(emptyList())

    save(schemesManager)

    assertThat(dir, not(exists()))
  }

  public Test fun `save only if scheme differs from bundled`() {
    val dir = tempDirManager.newDirectory()
    var schemeManager = createSchemeManager(dir)
    val converter: (Element) -> TestScheme = { XmlSerializer.deserialize(it, javaClass<TestScheme>())!! }
    val bundledPath = "/bundledSchemes/default"
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    var schemes = schemeManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    val customScheme = TestScheme("default")
    assertThat(schemes.get(0), equalTo(customScheme))

    save(schemeManager)
    assertThat(dir, not(exists()))

    save(schemeManager)
    schemeManager.setSchemes(listOf(customScheme))
    assertThat(dir, not(exists()))

    schemes = schemeManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0), sameInstance(customScheme))

    customScheme.data = "foo"
    save(schemeManager)
    val schemeFile = File(dir, "default.xml")
    assertThat(schemeFile, exists())

    schemeManager = createSchemeManager(dir)
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    schemeManager.loadSchemes()

    schemes = schemeManager.getAllSchemes()
    assertThat(schemes.get(0), equalTo(customScheme))
  }

  private fun createSchemeManager(dir: File) = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), RoamingType.PER_USER, null, dir)

  public Test fun `don't remove dir if no schemes but at least one non-hidden file exists`() {
    val dir = tempDirManager.newDirectory()
    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), RoamingType.PER_USER, null, dir)

    val scheme = TestScheme("s1")
    schemesManager.setSchemes(listOf(scheme))

    save(schemesManager)

    val schemeFile = File(dir, "s1.xml")
    assertThat(schemeFile.exists(), equalTo(true))

    schemesManager.setSchemes(emptyList())

    FileUtil.writeToFile(File(dir, "empty"), byteArrayOf())

    save(schemesManager)

    assertThat(schemeFile.exists(), equalTo(false))
    assertThat(dir.exists(), equalTo(true))
  }

  public Test fun rename() {
    val dir = tempDirManager.newDirectory()
    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), RoamingType.PER_USER, null, dir)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes().isEmpty(), equalTo(true))

    val scheme = TestScheme("s1")
    schemesManager.setSchemes(listOf(scheme))

    val schemes = schemesManager.getAllSchemes()
    assertThat(schemes.size(), equalTo(1))
    assertThat(schemes.get(0), sameInstance(scheme))

    assertThat(File(dir, "s1.xml").exists(), equalTo(false))

    scheme.data = "newTrue"
    save(schemesManager)

    assertThat(File(dir, "s1.xml").exists(), equalTo(true))

    scheme.setName("s2")

    save(schemesManager)

    assertThat(File(dir, "s1.xml").exists(), equalTo(false))
    assertThat(File(dir, "s2.xml").exists(), equalTo(true))
  }

  private fun save(schemeManager: SchemeManagerImpl<TestScheme, TestScheme>) {
    invokeAndWaitIfNeed {
      schemeManager.save()
    }
  }

  private fun createAndLoad(testData: String): SchemeManagerImpl<TestScheme, TestScheme> {
    createTempFiles(testData)
    return createAndLoad()
  }

  private fun doLoadSaveTest(testData: String, expected: String, localExpected: String = "") {
    val schemesManager = createAndLoad(testData)
    schemesManager.save()
    checkSchemes(File(remoteBaseDir, "REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, localExpected, false)
  }

  private fun checkSchemes(expected: String) {
    checkSchemes(File(remoteBaseDir, "REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, "", false)
  }

  private fun createAndLoad(): SchemeManagerImpl<TestScheme, TestScheme> {
    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), RoamingType.PER_USER, MockStreamProvider(remoteBaseDir!!), localBaseDir!!)
    schemesManager.loadSchemes()
    return schemesManager
  }

  private fun createTempFiles(testData: String) {
    val temp = tempDirManager.newDirectory()
    localBaseDir = File(temp, "__local")
    remoteBaseDir = temp
    FileUtil.copyDir(File("${getTestDataPath()}/$testData"), File(temp, "REMOTE"))
  }
}

private fun checkSchemes(baseDir: File, expected: String, ignoreDeleted: Boolean) {
  val filesToScheme = StringUtil.split(expected, ";")
  val fileToSchemeMap = THashMap<String, String>()
  for (fileToScheme in filesToScheme) {
    val index = fileToScheme.indexOf("->")
    fileToSchemeMap.put(fileToScheme.substring(0, index), fileToScheme.substring(index + 2))
  }

  val files = baseDir.listFiles()
  if (files != null) {
    for (file in files) {
      val fileName = FileUtil.getNameWithoutExtension(file)
      if ("--deleted" == fileName && ignoreDeleted) {
        assertThat<Map<String, String>>(fileToSchemeMap, hasKey(fileName))
      }
    }
  }

  for (file in fileToSchemeMap.keySet()) {
    assertThat(File(baseDir, "$file.xml").isFile(), equalTo(true))
  }

  if (files != null) {
    val schemesProcessor = TestSchemesProcessor()
    for (file in files) {
      val fileName = FileUtil.getNameWithoutExtension(file)
      val scheme = schemesProcessor.readScheme(JDOMUtil.load(file), true)
      assertThat(fileToSchemeMap.get(fileName), equalTo(scheme!!.getName()))
    }
  }
}

public data Tag("scheme") class TestScheme(Attribute private var name: String = "", Attribute var data: String? = null) : ExternalizableScheme {
  override fun getName() = name

  override Transient fun setName(newName: String) {
    name = newName
  }

  @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
  override fun getExternalInfo() = null
}

public open class TestSchemesProcessor : BaseSchemeProcessor<TestScheme>() {
  override fun readScheme(element: Element) = XmlSerializer.deserialize(element, javaClass<TestScheme>())

  override fun writeScheme(scheme: TestScheme) = scheme.serialize()
}

fun SchemeManagerImpl<*, *>.save() {
  val errors = SmartList<Throwable>()
  save(errors)
  CompoundRuntimeException.doThrow(errors)
}

public fun <T : Any> T.serialize(): Element = XmlSerializer.serialize(this, SkipDefaultValuesSerializationFilters())

public fun Element.toByteArray(): ByteArray {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.writeParent(this, out, "\n")
  return out.toByteArray()
}