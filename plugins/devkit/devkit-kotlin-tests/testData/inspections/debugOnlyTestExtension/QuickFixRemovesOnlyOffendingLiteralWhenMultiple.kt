import com.example.SomeValidExt
import com.intellij.ide.starter.extended.engine.junit5.UseInstaller
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(SomeValidExt::class, <error>UseInstaller<caret>::class</error>)
class QuickFixRemovesOnlyOffendingLiteralWhenMultiple
