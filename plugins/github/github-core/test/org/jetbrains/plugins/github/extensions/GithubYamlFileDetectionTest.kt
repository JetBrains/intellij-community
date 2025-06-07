package org.jetbrains.plugins.github.extensions

import com.intellij.mock.MockVirtualFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GithubYamlFileDetectionTest {

  @Test
  @DisplayName("Test GitHub workflow file detection")
  fun testGithubWorkflowFileDetection() {
    val githubDir = MockVirtualFile.dir(".github/workflows")
    val validWorkflowFile = MockVirtualFile("example.yml")
    val validWorkflowFileYaml = MockVirtualFile("example.yaml")
    val invalidExtension = MockVirtualFile("example.txt")
    githubDir.addChild(validWorkflowFile)
    githubDir.addChild(validWorkflowFileYaml)
    githubDir.addChild(invalidExtension)
    githubDir.refresh(false, true)

    val invalidDir = MockVirtualFile.dir("/src/workflows/")
    val fileInInvalidDir = MockVirtualFile(false, "example.yml")
    invalidDir.addChild(fileInInvalidDir)
    invalidDir.refresh(false, true)

    val incorrectDir = MockVirtualFile.dir(".github/not_workflows")
    val fileInIncorrectPath = MockVirtualFile(false, "example.yml")
    incorrectDir.addChild(fileInIncorrectPath)
    incorrectDir.refresh(false, true)

    val nestedWorkflowDir = MockVirtualFile.dir(".github/workflows/nested")
    val nestedWorkflowFile = MockVirtualFile("nested_example.yml")
    nestedWorkflowDir.addChild(nestedWorkflowFile)
    nestedWorkflowDir.refresh(false, true)
    githubDir.addChild(nestedWorkflowDir)

    val hiddenWorkflowFile = MockVirtualFile(".hidden_example.yml")
    githubDir.addChild(hiddenWorkflowFile)

    val nonWorkflowFileInWorkflowsDir = MockVirtualFile("README.md")
    githubDir.addChild(nonWorkflowFileInWorkflowsDir)

    githubDir.refresh(false, true)

    assertTrue(isGithubWorkflowFile(validWorkflowFile), "Valid workflow file should be detected")
    assertTrue(isGithubWorkflowFile(validWorkflowFileYaml), "Valid .yaml workflow file should be detected")
    assertFalse(isGithubWorkflowFile(invalidExtension), "File with invalid extension shouldn't be detected")
    assertFalse(isGithubWorkflowFile(fileInInvalidDir), "File in incorrect directory shouldn't be detected")
    assertFalse(isGithubWorkflowFile(fileInIncorrectPath), "File without proper '/workflows' path shouldn't be detected")
    assertTrue(isGithubWorkflowFile(nestedWorkflowFile), "File in nested '/workflows' directory should be detected")
    assertTrue(isGithubWorkflowFile(hiddenWorkflowFile), "Hidden workflow file should be detected")
    assertFalse(isGithubWorkflowFile(nonWorkflowFileInWorkflowsDir), "Non-workflow file within the workflows directory shouldn't be detected")
  }

  @Test
  @DisplayName("Test GitHub action file detection")
  fun testGithubActionFileDetection() {

    val validActionFile = MockVirtualFile("action.yml")
    val validActionFileYaml = MockVirtualFile("action.yaml")

    val invalidExtension = MockVirtualFile("action.txt")
    val invalidJsonExtension = MockVirtualFile("action.json")

    val closeButInvalidName = MockVirtualFile("actions.yaml")
    val invalidActionName = MockVirtualFile("some-action.yml")
    val invalidNameInWorkflowDir = MockVirtualFile.dir(".github/workflows/")
    val nonActionFile = MockVirtualFile("the_action.yml")
    invalidNameInWorkflowDir.addChild(nonActionFile)
    invalidNameInWorkflowDir.addChild(invalidActionName)
    invalidNameInWorkflowDir.refresh(false, true)

    val nestedInvalidDir = MockVirtualFile.dir(".github/workflows/nested")
    val nestedNonActionFile = MockVirtualFile("action_inside_nested.yml")
    nestedInvalidDir.addChild(nestedNonActionFile)
    nestedInvalidDir.refresh(false, true)

    val hiddenInvalidFile = MockVirtualFile(".hidden-action.txt")

    val unrelatedDir = MockVirtualFile.dir("src/some_folder")
    val unrelatedActionFile = MockVirtualFile("action.yml")
    unrelatedDir.addChild(unrelatedActionFile)
    unrelatedDir.refresh(false, true)

    val rootDir = MockVirtualFile.dir("/")
    rootDir.addChild(validActionFile)
    rootDir.addChild(validActionFileYaml)
    rootDir.addChild(invalidExtension)
    rootDir.addChild(invalidJsonExtension)
    rootDir.addChild(closeButInvalidName)
    rootDir.addChild(hiddenInvalidFile)
    rootDir.addChild(unrelatedDir)
    rootDir.addChild(invalidNameInWorkflowDir)
    rootDir.addChild(nestedInvalidDir)
    rootDir.refresh(false, true)

    assertTrue(isGithubActionFile(validActionFile), "File named 'action.yml' should be detected as an Action file")
    assertTrue(isGithubActionFile(validActionFileYaml), "File named 'action.yaml' should be detected as an Action file")
    assertFalse(isGithubActionFile(invalidActionName), "Files with invalid names shouldn't be detected as Action files")
    assertFalse(isGithubActionFile(invalidExtension), "File with invalid extension shouldn't be detected as an Action file")
    assertFalse(isGithubActionFile(invalidJsonExtension), "Files with .json extension shouldn't be treated as Action files")
    assertFalse(isGithubActionFile(closeButInvalidName), "Files with names close to 'action.yaml' shouldn't be detected")
    assertFalse(isGithubActionFile(nonActionFile), "File with different name under '.github/workflows' shouldn't be detected")
    assertFalse(isGithubActionFile(nestedNonActionFile), "Files inside nested directories under '.github/workflows' shouldn't be detected")
    assertFalse(isGithubActionFile(hiddenInvalidFile), "Hidden file with invalid extension shouldn't be detected")
    assertTrue (isGithubActionFile(unrelatedActionFile), "Files in unrelated folders should be detected as Action files")
  }

  @Test
  @DisplayName("Test combined GitHub Actions file detection")
  fun testCombinedGithubActionsFileDetectionWithMultipleScenarios() {

    val githubWorkflowsDir = MockVirtualFile.dir(".github/workflows")
    val workflowFile = MockVirtualFile("test.yaml")
    val anotherWorkflowFile = MockVirtualFile("build.yml")
    val invalidWorkflowFile = MockVirtualFile("invalid-workflow.txt")
    githubWorkflowsDir.addChild(workflowFile)
    githubWorkflowsDir.addChild(anotherWorkflowFile)
    githubWorkflowsDir.addChild(invalidWorkflowFile)
    githubWorkflowsDir.refresh(false, true)

    val rootDir = MockVirtualFile.dir("/")

    val actionFile = MockVirtualFile("action.yml")
    val actionFileYaml = MockVirtualFile("action.yaml")
    val invalidActionFile = MockVirtualFile("action.txt")
    val unrelatedDir = MockVirtualFile.dir("src")
    val unrelatedFile = MockVirtualFile("some_file.yaml")
    unrelatedDir.addChild(unrelatedFile)
    val nestedWorkflowDir = MockVirtualFile.dir(".github/workflows/nested")
    val nestedWorkflowFile = MockVirtualFile("nested_workflow.yml")
    nestedWorkflowDir.addChild(nestedWorkflowFile)
    githubWorkflowsDir.addChild(nestedWorkflowDir)
    val hiddenWorkflowFile = MockVirtualFile(".hidden_workflow.yml")
    githubWorkflowsDir.addChild(hiddenWorkflowFile)
    rootDir.addChild(githubWorkflowsDir)
    rootDir.addChild(actionFile)
    rootDir.addChild(actionFileYaml)
    rootDir.addChild(invalidActionFile)
    rootDir.addChild(unrelatedDir)

    rootDir.refresh(false, true)

    assertTrue(isGithubActionsFile(workflowFile), "Workflow file should be detected as a GitHub Actions file")
    assertTrue(isGithubActionsFile(anotherWorkflowFile), "Another valid workflow file should be detected as a GitHub Actions file")
    assertFalse(isGithubActionsFile(invalidWorkflowFile), "Invalid workflow file with incorrect extension should not be detected")
    assertTrue(isGithubActionsFile(actionFile), "Action file 'action.yml' should be detected as a GitHub Actions file")
    assertTrue(isGithubActionsFile(actionFileYaml), "Action file 'action.yaml' should be detected as a GitHub Actions file")
    assertFalse(isGithubActionsFile(invalidActionFile), "Invalid action file with incorrect extension should not be detected")
    assertTrue(isGithubActionsFile(nestedWorkflowFile), "Nested workflow file should be detected as a GitHub Actions file")
    assertTrue(isGithubActionsFile(hiddenWorkflowFile), "Hidden workflow file should be detected as a GitHub Actions file")
    assertFalse(isGithubActionsFile(unrelatedFile), "Unrelated file in an unrelated directory should not be detected as a GitHub Actions file")
    assertFalse(isGithubActionsFile(MockVirtualFile("random.file")), "Completely unrelated file should not be detected as a GitHub Actions file")
  }
}