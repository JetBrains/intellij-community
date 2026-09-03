// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

@JvmInline
value class UserId(val id: String)

operator fun UserId.component1(): String = id

fun testUserId(userId: UserId) {
    val <caret>(id) = userId
}
