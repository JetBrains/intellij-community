// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModMoveFile
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.modplan.move.ModPlanMovePlaces
import com.intellij.refactoring.modplan.move.ModPlanMoveProblem
import com.intellij.refactoring.modplan.move.ModPlanMoveProvider
import com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo

class ModPlanMoveTest : BasePlatformTestCase() {
  private fun root(): VirtualFile = myFixture.tempDirFixture.getFile("/")!!

  private fun urlOf(path: String): String = root().url + "/" + path

  private fun itemOf(path: String): PsiFileSystemItem {
    val file = root().findFileByRelativePath(path)!!
    val psiManager = PsiManager.getInstance(project)
    return if (file.isDirectory) psiManager.findDirectory(file)!! else psiManager.findFile(file)!!
  }

  private fun requestOfItems(
    items: List<PsiElement>,
    targetPath: String,
    optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
  ): ModPlanRequest = runReadActionBlocking {
    ModPlanMoveRefactoring.request(project, items, urlOf(targetPath), optionValues)
  }

  private fun requestOf(paths: List<String>, targetPath: String, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY)
    : ModPlanRequest = requestOfItems(paths.map { itemOf(it) }, targetPath, optionValues)

  private fun plan(request: ModPlanRequest): ModPlan = inBackground { ModPlanMoveRefactoring.getInstance().plan(request) }

  private fun accepted(paths: List<String>, targetPath: String): ModPlan.AcceptedPlan =
    plan(requestOf(paths, targetPath)) as ModPlan.AcceptedPlan

  private fun execute(plan: ModPlan.AcceptedPlan): ModPlanExecutionResult = ModPlanExecutor.execute(plan.execution, true, plan.writeEvents)

  private fun move(paths: List<String>, targetPath: String): ModPlanExecutionResult = execute(accepted(paths, targetPath))

  private fun problemOfItems(items: List<PsiElement>, targetUrl: String): ModPlanMoveProblem? =
    runReadActionBlocking { ModPlanMoveRefactoring.checkPlacement(project, items, targetUrl) }

  private fun problemOf(paths: List<String>, targetPath: String): ModPlanMoveProblem? =
    problemOfItems(paths.map { itemOf(it) }, urlOf(targetPath))

  private fun isSupported(request: ModPlanRequest): Boolean =
    runReadActionBlocking { ModPlanMoveRefactoring.getInstance().isSupported(request) }

  fun `test a file moves into an existing directory`() {
    myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("e/b.txt", "b")
    val movedPaths = recordMovedPaths()

    val result = move(listOf("d/a.txt"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals("a", VfsUtilCore.loadText(root().findFileByRelativePath("e/a.txt")!!))
    assertNull(root().findFileByRelativePath("d/a.txt"))
    assertEquals(listOf("/src/e/a.txt"), movedPaths)
  }

  fun `test the refactoring events of a move carry the moved items and the target directory`() {
    val file = myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("e/b.txt", "b")
    val targetDirectory = itemOf("e")
    val eventData = recordEventData()

    val result = move(listOf("d/a.txt"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(listOf<PsiElement>(file), eventData.started!!.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY)!!.toList())
    assertEquals(targetDirectory, eventData.started!!.getUserData(RefactoringEventData.TARGET_ELEMENT_KEY))
    assertEquals(targetDirectory, eventData.done!!.getUserData(RefactoringEventData.PSI_ELEMENT_KEY))
  }

  fun `test a move into a new directory reports the new directory after the writes`() {
    val file = myFixture.addFileToProject("a.txt", "a")
    val movedPaths = recordMovedPaths()
    val eventData = recordEventData()

    val result = move(listOf("a.txt"), "x")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertNull(eventData.started!!.getUserData(RefactoringEventData.TARGET_ELEMENT_KEY))
    assertEquals(listOf<PsiElement>(file), eventData.started!!.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY)!!.toList())
    val doneDirectory = eventData.done!!.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) as PsiFileSystemItem
    assertEquals(root().findChild("x"), doneDirectory.virtualFile)
    assertEquals(listOf("/src/x/a.txt"), movedPaths)
  }

  fun `test the listeners hear about a moved directory and not about its content`() {
    myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("e/b.txt", "b")
    val movedPaths = recordMovedPaths()

    val result = move(listOf("d"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(listOf("/src/e/d"), movedPaths)
  }

  fun `test a directory moves with its content`() {
    myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("d/sub/b.txt", "b")
    myFixture.addFileToProject("e/c.txt", "c")

    val result = move(listOf("d"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertNotNull(root().findFileByRelativePath("e/d/a.txt"))
    assertNotNull(root().findFileByRelativePath("e/d/sub/b.txt"))
    assertNull(root().findChild("d"))
  }

  fun `test a file moves into a new nested directory`() {
    myFixture.addFileToProject("a.txt", "a")
    val plan = accepted(listOf("a.txt"), "x/y")

    val createdDirectories = plan.execution.command.unpack().filterIsInstance<ModCreateFile>().map { it.file() }
    val result = execute(plan)

    assertEquals(listOf("x", "y"), createdDirectories.map { it.name })
    assertEquals(createdDirectories[0], createdDirectories[1].parent)
    assertEquals(ModPlanExecutionResult.Applied, result)
    assertNotNull(root().findFileByRelativePath("x/y/a.txt"))
    assertNull(root().findChild("a.txt"))
  }

  fun `test a subject inside another subject moves with it`() {
    myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("e/a.txt", "e")
    val directory = root().findChild("d")!!
    val plan = accepted(listOf("d", "d/a.txt", "d"), "e")

    val movedFiles = plan.execution.command.unpack().filterIsInstance<ModMoveFile>().map { it.file() }
    val result = execute(plan)

    assertEquals(listOf(directory), movedFiles)
    assertEquals(ModPlanExecutionResult.Applied, result)
    assertNotNull(root().findFileByRelativePath("e/d/a.txt"))
    assertEquals("e", VfsUtilCore.loadText(root().findFileByRelativePath("e/a.txt")!!))
  }

  fun `test a binary file is supported and moves`() {
    val file = myFixture.tempDirFixture.createFile("d/a.zip", "a")
    myFixture.addFileToProject("e/b.txt", "b")
    val request = requestOf(listOf("d/a.zip"), "e")

    assertTrue(file.fileType.isBinary)
    assertTrue(isSupported(request))
    val result = execute(plan(request) as ModPlan.AcceptedPlan)

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(root().findFileByRelativePath("e/a.zip"), file)
    assertNull(root().findFileByRelativePath("d/a.zip"))
  }

  fun `test each problem of checkPlacement`() {
    myFixture.addFileToProject("d/a.txt", "a")
    myFixture.addFileToProject("d/sub/b.txt", "b")
    myFixture.addFileToProject("e/a.txt", "e")
    myFixture.addFileToProject("f/a.txt", "f")
    val deletedFile = myFixture.addFileToProject("g.txt", "g")
    runWriteAction { deletedFile.virtualFile.delete(this) }

    assertEquals(ModPlanMoveProblem.INVALID_SUBJECT, problemOfItems(listOf(deletedFile), urlOf("e")))
    assertEquals(ModPlanMoveProblem.INVALID_DESTINATION, problemOf(listOf("d/sub/b.txt"), "../x"))
    assertEquals(ModPlanMoveProblem.INVALID_DESTINATION, problemOf(listOf("d/sub/b.txt"), "d/a.txt/x"))
    assertEquals(ModPlanMoveProblem.INVALID_DESTINATION, problemOfItems(listOf(itemOf("d/sub/b.txt")), ""))
    assertEquals(ModPlanMoveProblem.MOVE_INTO_ITSELF, problemOf(listOf("d"), "d/sub"))
    assertEquals(ModPlanMoveProblem.MOVE_INTO_ITSELF, problemOf(listOf("d"), "d/new/x"))
    assertEquals(ModPlanMoveProblem.ALREADY_IN_DESTINATION, problemOf(listOf("e/a.txt", "d/sub/b.txt"), "d/sub"))
    assertEquals(ModPlanMoveProblem.TARGET_EXISTS, problemOf(listOf("d/a.txt"), "e"))
    assertEquals(ModPlanMoveProblem.TARGET_EXISTS, problemOf(listOf("e/a.txt", "f/a.txt"), "x"))
    assertNull(problemOf(listOf("d", "d/a.txt"), "e"))
    assertNull(problemOf(listOf("d/a.txt"), "x/y"))
  }

  fun `test a content root is not movable`() {
    myFixture.addFileToProject("a.txt", "a")
    val contentRoot = runReadActionBlocking { PsiManager.getInstance(project).findDirectory(root())!! }

    assertEquals(ModPlanMoveProblem.INVALID_SUBJECT, problemOfItems(listOf(contentRoot), urlOf("x")))
    val refusal = plan(requestOfItems(listOf(contentRoot), "x")) as ModPlan.RefusedPlan
    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusal.reason)
  }

  fun `test a refusal carries the message of the problem`() {
    myFixture.addFileToProject("d/a.txt", "a")

    val refusal = plan(requestOf(listOf("d/a.txt"), "d")) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusal.reason)
    assertEquals(ModPlanMoveProblem.ALREADY_IN_DESTINATION.message(), refusal.message)
    assertNotNull(root().findFileByRelativePath("d/a.txt"))
  }

  fun `test a file of a language handler is not supported`() {
    myFixture.addFileToProject("d/a.handled", "a")
    myFixture.addFileToProject("e/b.txt", "b")
    val fileRequest = requestOf(listOf("d/a.handled"), "e")
    val directoryRequest = requestOf(listOf("d"), "e")
    assertTrue(isSupported(fileRequest))
    assertTrue(isSupported(directoryRequest))

    ExtensionTestUtil.addExtensions(ExtensionPointName.create<MoveFileHandler>("com.intellij.moveFileHandler"),
                                    listOf<MoveFileHandler>(LanguageHandler()), testRootDisposable)

    assertFalse(isSupported(fileRequest))
    assertFalse(isSupported(directoryRequest))
    for (request in listOf(fileRequest, directoryRequest)) {
      val refusal = plan(request) as ModPlan.RefusedPlan
      assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
    }
  }

  fun `test the first provider in the order owns a subject`() {
    myFixture.addFileToProject("d/a.own", "a")
    myFixture.addFileToProject("d/b.txt", "b")
    myFixture.addFileToProject("e/c.txt", "c")
    val provider = registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") }))

    val result = move(listOf("d/a.own", "d/b.txt"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(listOf("a.own"), provider.placedNames)
    assertNotNull(root().findFileByRelativePath("e/a.own"))
    assertNotNull(root().findFileByRelativePath("e/b.txt"))
    assertNull(root().findFileByRelativePath("d/a.own"))
    assertNull(root().findFileByRelativePath("d/b.txt"))
  }

  fun `test a refusal in place refuses the plan with its problem`() {
    myFixture.addFileToProject("d/a.own", "a")
    myFixture.addFileToProject("e/c.txt", "c")
    registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") },
                                      refusal = ModPlanMoveProblem.INVALID_DESTINATION))

    val refusal = plan(requestOf(listOf("d/a.own"), "e")) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusal.reason)
    assertEquals(ModPlanMoveProblem.INVALID_DESTINATION.message(), refusal.message)
    assertNotNull(root().findFileByRelativePath("d/a.own"))
  }

  fun `test two owners with one future path are refused`() {
    myFixture.addFileToProject("d/a.own", "a")
    myFixture.addFileToProject("f/b.txt", "b")
    myFixture.addFileToProject("e/c.txt", "c")
    registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") }, newName = { "b.txt" }))

    assertEquals(ModPlanMoveProblem.TARGET_EXISTS, problemOf(listOf("d/a.own", "f/b.txt"), "e"))
  }

  fun `test a subject inside a directory of another owner moves with the directory`() {
    myFixture.addFileToProject("own/a.txt", "a")
    myFixture.addFileToProject("e/c.txt", "c")
    val directory = root().findChild("own")!!
    registerProvider(TestMoveProvider({ it is PsiDirectory && it.name == "own" }))

    val plan = accepted(listOf("own", "own/a.txt"), "e")

    val movedFiles = plan.execution.command.unpack().filterIsInstance<ModMoveFile>().map { it.file() }
    assertEquals(listOf(directory), movedFiles)
    assertEquals(ModPlanExecutionResult.Applied, execute(plan))
    assertNotNull(root().findFileByRelativePath("e/own/a.txt"))
  }

  fun `test a provider that is legacy only makes the move not supported`() {
    myFixture.addFileToProject("d/a.own", "a")
    myFixture.addFileToProject("e/c.txt", "c")
    registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") }, legacyOnly = true))
    val request = requestOf(listOf("d/a.own"), "e")

    assertFalse(isSupported(request))
    val refusal = plan(request) as ModPlan.RefusedPlan
    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
  }

  fun `test a subject that no provider owns is not supported`() {
    val element = runReadActionBlocking { myFixture.addFileToProject("d/a.txt", "a").firstChild }
    myFixture.addFileToProject("e/c.txt", "c")
    val request = requestOfItems(listOf(element), "e")

    assertFalse(isSupported(request))
    val refusal = plan(request) as ModPlan.RefusedPlan
    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
  }

  fun `test the analysis of an owner sees the places of the other owner`() {
    myFixture.addFileToProject("d/a.own", "a")
    val file = myFixture.addFileToProject("d/b.txt", "b").virtualFile
    myFixture.addFileToProject("e/c.txt", "c")
    val provider = registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") }))

    val result = move(listOf("d/a.own", "d/b.txt"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(root().path + "/e/b.txt", provider.analyzedPlaces.single().futurePathOf(file))
  }

  fun `test an emptied directory of a placement is deleted after the moves`() {
    myFixture.addFileToProject("d/a.own", "a")
    myFixture.addFileToProject("d/b.own", "b")
    myFixture.addFileToProject("e/c.txt", "c")
    registerProvider(TestMoveProvider({ it is PsiFileSystemItem && it.name.endsWith(".own") },
                                      emptiedDirectories = { listOf(root().findChild("d")!!) }))

    val result = move(listOf("d/a.own", "d/b.own"), "e")

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertNull(root().findChild("d"))
    assertNotNull(root().findFileByRelativePath("e/a.own"))
    assertNotNull(root().findFileByRelativePath("e/b.own"))
  }

  private fun registerProvider(provider: TestMoveProvider): TestMoveProvider {
    val providers = listOf(provider) + ModPlanMoveProvider.EP_NAME.extensionList
    ExtensionTestUtil.maskExtensions(ModPlanMoveProvider.EP_NAME, providers, testRootDisposable)
    return provider
  }

  private class TestMoveProvider(
    private val owns: (PsiElement) -> Boolean,
    private val newName: (VirtualFile) -> String = { it.name },
    private val legacyOnly: Boolean = false,
    private val refusal: ModPlanMoveProblem? = null,
    private val emptiedDirectories: () -> List<VirtualFile> = { emptyList() },
  ) : ModPlanMoveProvider {
    val placedNames = ArrayList<String>()
    val analyzedPlaces = ArrayList<ModPlanMovePlaces>()

    override fun isOwner(subject: SymbolPointer): Boolean = subject.element()?.let(owns) == true

    override fun isLegacyOnly(subject: SymbolPointer): Boolean = legacyOnly

    override fun place(
      subjects: List<SymbolPointer>,
      targetDirectory: VirtualFile,
      request: ModPlanRequest,
    ): ModPlanMoveProvider.Placement {
      val items = subjects.map { (it.element() as PsiFileSystemItem).virtualFile }
      placedNames.addAll(items.map { it.name })
      if (refusal != null) return ModPlanMoveProvider.Placement.Refused(refusal, null)
      return ModPlanMoveProvider.Placement.Accepted(items.map { ModPlanMoveProvider.ItemMove(it, targetDirectory, newName(it)) },
                                                    emptiedDirectories())
    }

    override fun analyze(
      subjects: List<SymbolPointer>,
      targetDirectory: VirtualFile,
      places: ModPlanMovePlaces,
      request: ModPlanRequest,
    ): ModPlanMoveProvider.Analysis {
      analyzedPlaces.add(places)
      return ModPlanMoveProvider.Analysis(ModPlanAnalysisResult.Found(listOf(), ModPlanConflicts.EMPTY), listOf(), null)
    }
  }

  private class EventData {
    var started: RefactoringEventData? = null
    var done: RefactoringEventData? = null
  }

  private fun recordEventData(): EventData {
    val eventData = EventData()
    val connection = project.messageBus.connect(testRootDisposable)
    connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        eventData.started = beforeData
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        eventData.done = afterData
      }
    })
    return eventData
  }

  private fun recordMovedPaths(): List<String> {
    val movedPaths = ArrayList<String>()
    val provider = RefactoringElementListenerProvider { _ ->
      object : RefactoringElementListener {
        override fun elementMoved(newElement: PsiElement) {
          movedPaths.add((newElement as PsiFileSystemItem).virtualFile.path)
        }

        override fun elementRenamed(newElement: PsiElement) {
        }
      }
    }
    RefactoringElementListenerProvider.EP_NAME.getPoint(project).registerExtension(provider, testRootDisposable)
    return movedPaths
  }

  private class LanguageHandler : EmptyHandler(".handled")

  private open class EmptyHandler(private val suffix: String) : MoveFileHandler() {
    override fun canProcessElement(element: PsiFile): Boolean = element.name.endsWith(suffix)

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
    }

    override fun findUsages(psiFile: PsiFile, newParent: PsiDirectory, searchInComments: Boolean, searchInNonJavaFiles: Boolean)
      : List<UsageInfo>? = null

    override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {
    }

    override fun updateMovedFile(file: PsiFile) {
    }
  }
}