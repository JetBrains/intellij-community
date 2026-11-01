import com.intellij.openapi.progress.ProcessCanceledException

class InsertRethrowHelperInPceCatch {
  fun m(): String? {
    try {
      return compute()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown"><caret>e</error>: ProcessCanceledException) {
      return null
    }
  }

  fun compute(): String = ""
}
