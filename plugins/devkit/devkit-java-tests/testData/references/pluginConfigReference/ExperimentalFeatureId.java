import com.intellij.openapi.application.Experiments;

public class ExperimentalFeatureId {
  public static void main(String[] args) {
    Experiments.getInstance().isFeatureEnabled("my.feature.id");
    Experiments.getInstance().isFeatureEnabled("<error descr="Cannot resolve feature 'INVALID_VALUE'">INVALID_VALUE</error>");
  }
}