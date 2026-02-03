import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import inspections.wrapInSupplierFix.MyAnnotation;

@Service(Service.Level.APP)
class MyService {

    // to check comments and annotations
    @MyAnnotation
    static final MyService <warning descr="Application service must not be assigned to a static final field">instance<caret></warning> = ApplicationManager.getApplication().getService(MyService.class);

    // to check name generation with conflicts
    static final int instanceSupplier = 0;

    public void foo() { }
}