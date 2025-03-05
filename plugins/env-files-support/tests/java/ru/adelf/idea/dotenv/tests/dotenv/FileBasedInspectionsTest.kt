package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.inspections.EmptyNestedVariableInspection
import ru.adelf.idea.dotenv.inspections.NestedVariableOutsideDoubleQuotesInspection
import ru.adelf.idea.dotenv.inspections.UndefinedNestedVariableInspection
import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class FileBasedInspectionsTest: DotEnvFileBasedTestCase() {

    fun testUndefinedNestedVariableInspection() {
        doInspectionTest(UndefinedNestedVariableInspection())
    }

    fun testEmptyNestedVariableInspection() {
        doInspectionTest(EmptyNestedVariableInspection())
    }

    fun testNestedVariableOutsideDoubleQuotesInspection() {
        doInspectionTest(NestedVariableOutsideDoubleQuotesInspection())
    }

    fun testNestedVariableOutsideDoubleQuotesQuickFix() {
        doQuickFixTest(NestedVariableOutsideDoubleQuotesInspection())
    }

    fun testNestedVariableOutsideDoubleQuotesQuickFixWithSurroundingSpaces() {
        doQuickFixTest(NestedVariableOutsideDoubleQuotesInspection())
    }

}