// WITH_STDLIB

fun testBoolean(file: String): Boolean {
    return file.let {
        if (it == "h") return true
        false
    }
}

fun testChain(file: String): Boolean {
    return file.isNotEmpty() && file.let {
        if (it == "h") return true
        if (it == "x") return false
        false
    }
}

fun testChain3(file: String): Boolean {
    return file != "xyz" && file.isNotEmpty() && file.run {
        if (this == "h") return true
        if (this == "x") return false
        false
    }
}

fun testElvis(param: String?, other: String) = param ?: other.let {
    if (it.isNotEmpty()) return it

    null
}

fun testChainMiddle(file: String): Boolean {
    return <warning descr="Condition 'file != \"xyz\" && file.isNotEmpty() && file.let {
        if (it == \"h\") return true
        if (it == \"x\") return false
        false
    } && file != \"zzz\"' is always false"><warning descr="Condition 'file != \"xyz\" && file.isNotEmpty() && file.let {
        if (it == \"h\") return true
        if (it == \"x\") return false
        false
    }' is always false">file != "xyz" && file.isNotEmpty() && <warning descr="Condition 'file.let {
        if (it == \"h\") return true
        if (it == \"x\") return false
        false
    }' is always false when reached">file.let {
        if (it == "h") return true
        if (it == "x") return false
        false
    }</warning></warning> && file != "zzz"</warning>
}

fun testChainOr(file: String): Boolean {
    return file.isNotEmpty() || file.let {
        if (it == "h") return true
        if (it == "x") return false
        false
    }
}

fun test3(file: String): String? {
    if (file == "hello") return "oops"
    val value: String? = <warning descr="Value of 'file.let {
        if (it.isNotEmpty()) return it

        null
    }' is always null">file.let {
        if (it.isNotEmpty()) return it

        null
    }</warning>
    return <weak_warning descr="Value of 'value' is always null">value</weak_warning>
}

fun test1(file: String): String? =
    file.let {
        if (it.isNotEmpty()) return@let it

        null
    }

fun test(file: String): String? =
    file.let {
        if (it.isNotEmpty()) return it 

        null
    }

fun test2(file: String): String? {
    if (file == "hello") return "oops"
    return file.let {
        if (it.isNotEmpty()) return it

        null
    }
}

