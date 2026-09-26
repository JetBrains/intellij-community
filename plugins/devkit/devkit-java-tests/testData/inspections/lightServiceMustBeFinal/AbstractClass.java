import com.intellij.openapi.components.Service;

@<error descr="Light service must be a concrete class and cannot be abstract or an interface.
The IntelliJ Platform relies on the concrete implementation class to create and manage the service instance. Without a concrete implementation, the platform would not be able to create an instance of the service. The service would not be available for use by the plugin.
To solve this problem, you should define a concrete implementation class for the service and annotate it with '@Service'.">Service<caret></error>
abstract class MyService {
}
