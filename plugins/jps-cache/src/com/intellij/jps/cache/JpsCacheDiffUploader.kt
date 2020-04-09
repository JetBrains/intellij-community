package com.intellij.jps.cache

import java.io.File

fun main(argv: Array<String>) {
  val commit = argv[0]
  val url = argv[1]
  val zip = File(argv[2])
  // Placeholder, that should be called from gant script on TC.
  println("commit: $commit, zip: $zip(${zip.length()}) url $url")
}
