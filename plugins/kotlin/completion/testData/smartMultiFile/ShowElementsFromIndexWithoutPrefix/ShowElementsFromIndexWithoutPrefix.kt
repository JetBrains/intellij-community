package a

import a.b.Token

val test: Token = <caret>

// EXIST: Token
// EXIST: SubToken
// EXIST: tokenValue
// EXIST: tokenFun
// EXIST: subTokenValue
// EXIST: subTokenFun

// EXIST: SubTokenOtherPackage
// EXIST: otherPackageTokenValue
// EXIST: otherPackageTokenFun
// EXIST: otherPackageSubTokenValue
// EXIST: otherPackageSubTokenFun
