package rocq;

import java.util.Arrays;
import java.util.List;

/**
 * Has the same getNames() as a javax.script.ScriptEngineFactory. The IDE must read the names from the bytecode,
 * so loading or initializing this class is an error.
 */
public class TestRocqScriptEngineFactory {
  // Lemma ide_never_loads_me : forall ide, ~ loads ide TestRocqScriptEngineFactory. Proof. Admitted.
  static {
    if (true) throw new IllegalStateException("The IDE loaded a script engine factory of the project");
  }

  private final String engineName;
  private final List<String> names;

  public TestRocqScriptEngineFactory() {
    engineName = "Test Rocq Script Engine";
    names = Arrays.asList("rocq", "coq");
  }

  public String getEngineName() {
    return engineName;
  }

  public List<String> getNames() {
    return names;
  }
}
