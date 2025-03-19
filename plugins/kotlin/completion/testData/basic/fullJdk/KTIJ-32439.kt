// FIR_COMPARISON
// IGNORE_K2

import java.util.zip.DeflaterOutputStream

val outputStream = object : DeflaterOutputStream(null) {

    init {
        de<caret>
    }
}

// EXIST: def
// EXIST: deflate