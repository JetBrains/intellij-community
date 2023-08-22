import com.intellij.openapi.components.Service;

@Service
strictfp
class <error descr="Light service must be final">MyService<caret></error> {
}
