package com.intellij.driver.sdk.jbacademy

/**
 * Constants for JBAcademy plugin UI tests
 */
object JBAcademyConstants {
  // XPath expressions
  const val COURSE_TOOL_WINDOW_XPATH = "//div[@accessiblename='Course Tool Window']"
  const val COURSE_ACCESSIBLENAME_XPATH = "//div[@accessiblename='Course']"
  const val LESSON_ACCESSIBLENAME_XPATH = "//div[@accessiblename='Lesson']"
  const val TASK_ACCESSIBLENAME_XPATH = "//div[@accessiblename='Task' and @class='ActionButton' and @myaction='Task (Create task)']"
  const val PREVIEW_COURSE_XPATH = "//div[@accessiblename='Preview Course']"
  const val STATUS_BAR_ICON_XPATH = "//div[@defaulticon='eduCourse.svg']"
  const val TASK_TOOL_WINDOW_XPATH = "//div[@accessiblename='Task Tool Window']"
  const val CONTENT_TAB_LABEL_XPATH = "//div[@class='ContentTabLabel']"
  const val LESSON_HEADER_XPATH = "//div[@class='LessonHeader']"
  const val DESCRIPTION_TAB_XPATH = "//div[@class='DescriptionTab']"
  const val CHECK_PANEL_BUTTON_XPATH = "//div[@class='CheckPanelButtonComponent']"
  
  // Accessible names
  const val COURSE_TOOL_WINDOW = "Course Tool Window"
  const val COURSE = "Course"
  const val LESSON = "Lesson"
  const val TASK = "Task"
  const val PREVIEW_COURSE = "Preview Course"
  const val TASK_TOOL_WINDOW = "Task Tool Window"
  
  // Class names
  const val CONTENT_TAB_LABEL = "ContentTabLabel"
  const val LESSON_HEADER = "LessonHeader"
  const val DESCRIPTION_TAB = "DescriptionTab"
  const val CHECK_PANEL_BUTTON = "CheckPanelButtonComponent"
  
  // Icons
  const val EDU_COURSE_ICON = "eduCourse.svg"
  
  // Test constants
  // Common constants
  const val GET_STARTED = "Get Started"
  const val LEARN_TO_PROGRAM = "Learn to program"
  const val KOTLIN_COURSE = "Kotlin Course"
  const val COURSE_CREATION = "Course Creation"
  const val LESSON1_FOLDER = "lesson1"
  const val TASK1_FOLDER = "task1"
  const val SRC_FOLDER = "src"
  const val MAIN_FOLDER = "main"
  const val TASK_KT = "Task.kt"
  const val TEST_FOLDER = "test"
  const val TESTS_KT = "Tests.kt"
  const val TASK_MD = "task.md"
  const val TASK_INFO_YAML = "task-info.yaml"
  const val LESSON_INFO_YAML = "lesson-info.yaml"
  const val BUILD_GRADLE = "build.gradle"
  const val COURSE_INFO_YAML = "course-info.yaml"
  const val SETTINGS_GRADLE = "settings.gradle"

  // GoLand constants
  const val GO_COURSE = "Go Course"
  const val TESTS_GO = "task_test.go"
  const val GO_MOD = "go.mod"
  const val TASK_GO = "task.go"
  const val MAIN_GO = "main.go"

  // PyCharm constants
  const val PYTHON_COURSE = "Python Course"
  const val PYTHON_LANGUAGE = "Python"
  const val TASK_PY = "task.py"
  const val TEST_TASK_PY = "test_task.py"
  const val INIT_PY = "__init__.py"

  // WebStorm constants
  const val JS_COURSE = "JavaScript Course"
  const val JS_LANGUAGE = "JavaScript"
  const val TASK_JS = "task.js"
  const val TEST_JS = "test.js"
  const val PACKAGE_JSON = "package.json"

  // System properties
  const val IDEA_IS_INTERNAL = "idea.is.internal"
  const val EDU_PLUGIN_ID = "com.jetbrains.edu"
  const val NIGHTLY_CHANNEL = "nightly"

  // Course creation constants
  const val KOTLIN_LANGUAGE = "Kotlin"
  const val OPTIONAL_DESCRIPTION = "(Optional) Write a short description for your course"
  const val TEST_DESCRIPTION = "test description"
  const val SIMPLE_LESSON = "simpleLesson"
  
  // Code content constants
  const val TYPE_YOUR_SOLUTION_HERE = "// type your solution here"
  const val TYPE_YOUR_GO_SOLUTION_HERE = "// todo: replace this with an actual task"
  const val TESTS_NOT_IMPLEMENTED = "Tests not implemented for the task"

  // Task description constants
  const val TASK_DESCRIPTION_TEXT = "This is a task description file."

  // FILE content constants
  const val TEST_KT_CONTENT = """
                 import org.junit.Assert
                 import org.junit.Test
              
                 class Test {
                     @Test fun testSolution() {
                         //TODO: implement your test here
                         Assert.assertTrue("Tests not implemented for the task", false)
                     }
                 }
               """

  const val TEST_GO_CONTENT = "package test\n" +
                              "\n" +
                              "import (\n" +
                              "\ttask \"task1\"\n" +
                              "\t\"testing\"\n" +
                              ")\n" +
                              "\n" +
                              "//todo: replace this with an actual test\n" +
                              "func TestSum(t *testing.T) {\n" +
                              "\ttype args struct {\n" +
                              "\t\ta int\n" +
                              "\t\tb int\n" +
                              "\t}\n" +
                              "\ttests := []struct {\n" +
                              "\t\tname string\n" +
                              "\t\targs args\n" +
                              "\t\twant int\n" +
                              "\t}{\n" +
                              "\t\t{\"1\", args{1, 1}, 2},\n" +
                              "\t\t{\"2\", args{1, 2}, 3},\n" +
                              "\t}\n" +
                              "\tfor _, tt := range tests {\n" +
                              "\t\tt.Run(tt.name, func(t *testing.T) {\n" +
                              "\t\t\tif got := task.Sum(tt.args.a, tt.args.b); got != tt.want {\n" +
                              "\t\t\t\tt.Errorf(\"Sum() = %v, want %v\", got, tt.want)\n" +
                              "\t\t\t}\n" +
                              "\t\t})\n" +
                              "\t}\n" +
                              "}\n"

  const val MAIN_GO_CONTENT = "package task\n" +
                              "\n" +
                              "// Sum is a task example\n" +
                              "// todo: replace this with an actual task\n" +
                              "func Sum(a, b int) int {\n" +
                              "\treturn a + b\n" +
                              "}"

  const val TASK_GO_CONTENT = "package task\n" +
                              "\n" +
                              "// Sum is a task example\n" +
                              "// todo: replace this with an actual task\n" +
                              "func Sum(a, b int) int {\n" +
                              "\treturn a + b\n" +
                              "}"

  const val TASK_PY_CONTENT = "# todo: replace this with an actual task\n" +
                              "def sum(a, b):\n" +
                              "    return a + b"

  const val TEST_TASK_PY_CONTENT = "import unittest\n" +
                                   "\n" +
                                   "from task import sum\n" +
                                   "\n" +
                                   "\n" +
                                   "# todo: replace this with an actual test\n" +
                                   "class TestCase(unittest.TestCase):\n" +
                                   "    def test_add(self):\n" +
                                   "        self.assertEqual(sum(1, 2), 3, msg=\"adds 1 + 2 to equal 3\")"
}