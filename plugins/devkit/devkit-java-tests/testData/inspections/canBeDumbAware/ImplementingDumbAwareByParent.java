import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;

public class ImplementingDumbAwareByParent extends ByParent {
}

interface ParentPossiblyDumbAware extends PossiblyDumbAware {}
abstract class ByParent implements ParentPossiblyDumbAware, DumbAware {}