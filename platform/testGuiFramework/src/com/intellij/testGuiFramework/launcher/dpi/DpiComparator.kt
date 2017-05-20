package com.intellij.testGuiFramework.launcher.dpi

import java.io.File
import java.io.FileNotFoundException
import java.util.*

/**
 * @author Sergey Karashevich
 */
class DpiComparator(val dirToScan: File, val comparator: PngComparator) {

    val pngPairs: MutableMap<File, File> = HashMap()

    companion object {
        fun process(pathToDir: String, comparator: PngComparator) {
            val dir = File(pathToDir)
            if (!dir.exists()) throw FileNotFoundException("Unable to find directory: $pathToDir")
            DpiComparator(dir, comparator).scan().compare()
        }
    }

    private fun scan(): DpiComparator {
        val files = dirToScan.listFiles().toCollection(ArrayList<File>(dirToScan.listFiles().size))
        while (files.isNotEmpty()) {
            val extractedPair = files.extractPair() ?: continue
            pngPairs.put(extractedPair.first, extractedPair.second)
        }
        return this
    }

    private fun MutableList<File>.extractPair(): Pair<File, File>? {
        val file = this.firstOrNull() ?: throw ArrayIndexOutOfBoundsException("Array with files is empty")
        val customDpiRegex = Regex("@\\d+\\.*\\d*x\\.png")
        if (file.name.contains(customDpiRegex)) {
            val fileNameToSearch = file.name.replace(customDpiRegex, ".png")
            val foundPair : File? = this.find { file -> file.name == fileNameToSearch }
            this.remove(file)
            if (foundPair == null) return null
            this.remove(foundPair)
            return Pair(foundPair, file) //the first one goes without dpi
        } else {
            val trimmedName = file.name.removeSuffix(".png")
            val foundPair : File? = this.find { file -> file.name.startsWith(trimmedName)}
            this.remove(file)
            if (foundPair == null)  return null
            this.remove(foundPair)
            return Pair(file, foundPair) //the first one goes without dpi
        }
    }

    private fun compare() {
        pngPairs.forEach { pair -> comparator.compare(pair.key, pair.value) }
    }

}