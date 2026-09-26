package a.c

import a.b.Token

class SubTokenOtherPackage : Token()

val otherPackageTokenValue: Token = Token()
fun otherPackageTokenFun(): Token = Token()
val otherPackageSubTokenValue: SubTokenOtherPackage = SubTokenOtherPackage()
fun otherPackageSubTokenFun(): SubTokenOtherPackage = SubTokenOtherPackage()
