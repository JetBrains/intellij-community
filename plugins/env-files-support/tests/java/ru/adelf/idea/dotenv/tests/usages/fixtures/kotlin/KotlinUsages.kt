package ru.adelf.idea.dotenv.tests.usages.fixtures.kotlin

import java.lang.System.getenv

class KotlinUsages {
    fun getEnv() {
        getenv("KOTLIN_GET_ENV")

        System.getenv("KOTLIN_GET_ENV2")
    }

    fun getDotenvGet() {
        Dotenv().get("KOTLIN_DOTENV_GET")

        val d = Dotenv()

        d["KOTLIN_DOTENV_GET2"]
    }
}

class Dotenv {
    operator fun get(key: String): String {
        return "";
    }
}