import com.intellij.ide.starter.junit5.RemoteDevRun
import org.junit.jupiter.api.extension.ExtendWith

class HighlightsRemoteDevRunOnMethod {
  @ExtendWith(<error>RemoteDevRun::class</error>)
  fun test() {}
}
