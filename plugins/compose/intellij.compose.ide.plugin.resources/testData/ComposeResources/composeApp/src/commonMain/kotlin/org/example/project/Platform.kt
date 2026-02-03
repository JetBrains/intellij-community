package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform