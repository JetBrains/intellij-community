/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.media

import java.io.File
import java.util.*

object TranscriptReader {

    val cachedDictionary: MutableList<Pair<Double, String>> = ArrayList<Pair<Double, String>>()

    fun readTranscription(fileName: String = "/transcription.txt") {
        val resourcePath = javaClass.getResource(fileName).toURI().toURL().path
        val dictionary = File(resourcePath)
                .readLines()
                .map { line ->
                    val timeStr = Regex("^\\d\\d:\\d\\d").find(line)!!.value
                    val (minStr, secStr) = timeStr.split(":")
                    val millis = (minStr.toInt() * 60 + secStr.toInt()) * 1000.0
                    Pair<Double, String>(millis, line.substring(timeStr.length + 1))
                }
        if (cachedDictionary.isNotEmpty()) cachedDictionary.clear()
        cachedDictionary.addAll(dictionary)
    }

    fun getTimeInterval(text: String): Pair<Double, Double> {
        if (cachedDictionary.isEmpty()) readTranscription()

        val timeAndText: Pair<Double, String>? = cachedDictionary.find { pair -> pair.second.toLowerCase().contains(text.toLowerCase()) }
        check(timeAndText != null)
        val index = cachedDictionary.indexOf(timeAndText)
        if (index == 0) return Pair(0.0, timeAndText!!.first)
        else return Pair(cachedDictionary[index - 1].first, timeAndText!!.first)
    }
}


fun main(args: Array<String>) {
  TranscriptReader.getTimeInterval("Alt +")
}