package serviceDeclarations;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

// Project service by annotation
@Service(Service.Level.PROJECT)
public final class LightServiceProjecLevelAnnotation { }
