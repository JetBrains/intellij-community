import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.components.Service;
import inspections.wrapInSupplierFix.MyAnnotation;

import java.util.function.Supplier;

@Service(Service.Level.APP)
class MyService {

    // to check comments and annotations
    @MyAnnotation
    static final Supplier<MyService> instanceSupplier1 = CachedSingletonsRegistry.lazy(() -> ApplicationManager.getApplication().getService(MyService.class));

    // to check name generation with conflicts
    static final int instanceSupplier = 0;

    public void foo() { }
}