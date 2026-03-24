// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_COMPARISON
// FIR_IDENTICAL

const val EMAIL = "some@thing.com"
class User(val email: String)

class Scope(val basic_email: String, val adder: String){
    val abc = "aaaa"
    val em = "em"
    val additionalEmail = ""
    val email2 = 23
    fun test(){
        User(<caret>)
    }
}

// ORDER: EMAIL
// ORDER: additionalEmail
// ORDER: basic_email
// ORDER: abc
// ORDER: adder
// ORDER: em
// ABSENT: email2
// IGNORE_K1