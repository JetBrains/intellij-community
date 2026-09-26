import com.intellij.openapi.application.Experiments;

public class ExperimentalFeatureIdCompletion {
  public static void main(String[] args) {
    Experiments.getInstance().isFeatureEnabled("<caret>");
  }
}