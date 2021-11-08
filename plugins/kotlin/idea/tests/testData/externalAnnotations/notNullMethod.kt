fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.notNullMethod()<warning descr="[UNNECESSARY_SAFE_CALL] Unnecessary safe call on a non-null receiver of type String!. This expression will have nullable type in future releases">?.</warning><warning descr="[DEPRECATION] 'toLowerCase(): String' is deprecated. Use lowercase() instead.">toLowerCase</warning>()
}