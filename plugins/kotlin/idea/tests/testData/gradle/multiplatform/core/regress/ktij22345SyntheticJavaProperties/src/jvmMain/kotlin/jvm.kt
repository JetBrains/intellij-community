//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER", "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER")
actual open class MyFile1 actual constructor(userPath: String): MyJavaFile(userPath)
actual typealias MyFile2 = java.io.File

fun testFile1(file: KotlinFile1) {
    file.getAbsolutePath()
    file.absolutePath
    file.isAbsolute()
    file.isAbsolute
    file.getAbsoluteFile()
    file.absoluteFile
}

fun testFile2(file: KotlinFile2) {
    file.getAbsolutePath()
    file.absolutePath
    file.isAbsolute()
    file.isAbsolute
    file.getAbsoluteFile()
    file.absoluteFile
}
