class KotlinFile1(userPath: String): MyFile1(userPath)
class KotlinFile2(userPath: String): MyFile2(userPath)

expect open class MyFile1(userPath: String)
expect open class MyFile2(userPath: String)
