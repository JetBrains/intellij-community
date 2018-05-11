package xxx;
import org.junit.platform.engine.*;
import java.util.Optional;

public class FooBarTestEngine implements TestEngine {

  @Override
  public String getId() {
    return "FooBar";
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
    throw new RuntimeException("The Bad. The Ugly. No Good");
  }

  @Override
  public void execute(ExecutionRequest request) {

  }

  @Override
  public Optional<String> getGroupId() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getArtifactId() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getVersion() {
    return Optional.empty();
  }
}
