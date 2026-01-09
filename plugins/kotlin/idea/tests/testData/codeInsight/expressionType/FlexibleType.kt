import java.io.File

fun fileName() {
    val file = File("it")
    file.nam<caret>e
}

// K1_TYPE: file.name -> <html>String!</html>
// K2_TYPE: file.name -> <b>String</b>
