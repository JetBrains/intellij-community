// FIR_COMPARISON

import java.util.zip.DeflaterOutputStream

val outputStream = object : DeflaterOutputStream(null) {

    init {
        de<caret>
    }
}

// EXIST: def
// EXIST: deflate