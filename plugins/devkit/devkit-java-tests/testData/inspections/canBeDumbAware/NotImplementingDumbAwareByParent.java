import com.intellij.openapi.project.PossiblyDumbAware;

public class <weak_warning descr="Can be made DumbAware if it does not access indexes">NotImplementingDumbAwareByParent</weak_warning> extends ByParent {
}

abstract class ByParent implements PossiblyDumbAware {}