//region Test configuration
// - hidden: line markers
//endregion
class KotlinFile1(userPath: String): MyFile1(userPath)
class KotlinFile2(userPath: String): MyFile2(userPath)

<!HIGHLIGHTING("severity='WARNING'; descr='[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573'")!>expect<!> open class MyFile1(userPath: String)
<!HIGHLIGHTING("severity='WARNING'; descr='[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573'")!>expect<!> open class MyFile2(userPath: String)
