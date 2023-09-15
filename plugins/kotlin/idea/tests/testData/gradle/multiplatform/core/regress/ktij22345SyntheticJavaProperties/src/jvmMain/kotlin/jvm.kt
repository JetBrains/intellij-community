//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER", "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER")
<!HIGHLIGHTING("severity='WARNING'; descr='[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573'")!>actual<!> open class MyFile1 actual constructor(userPath: String): MyJavaFile(userPath)
<!HIGHLIGHTING("severity='WARNING'; descr='[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573'")!>actual<!> typealias MyFile2 = java.io.File

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
