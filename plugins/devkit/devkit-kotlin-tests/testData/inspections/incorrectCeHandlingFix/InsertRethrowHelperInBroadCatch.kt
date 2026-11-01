import com.intellij.openapi.progress.ProgressManager

class InsertRethrowHelperInBroadCatch {
  fun m() {
    try {
      ProgressManager.checkCanceled()
    }
    catch (<error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown. It is thrown by 'checkCanceled()'."><caret>e</error>: Throwable) {
      handle(e)
    }
  }

  fun handle(e: Throwable) {}
}
