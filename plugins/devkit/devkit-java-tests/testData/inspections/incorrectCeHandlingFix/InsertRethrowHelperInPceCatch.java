import com.intellij.openapi.progress.ProcessCanceledException;

class InsertRethrowHelperInPceCatch {
  String m() {
    try {
      return compute();
    }
    catch (ProcessCanceledException <error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown"><caret>e</error>) {
      return null;
    }
  }

  String compute() { return ""; }
}
