import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Service {

  private final @NotNull Map<<warning descr="Consider using 'String' instead of 'Language' as the map key">Language</warning>, Object> myLanguageMap = new HashMap<>();
  private final @NotNull HashMap<<warning descr="Consider using 'String' instead of 'Language' as the map key">Language</warning>, Object> myLanguageHashMap = new HashMap<>();
  private final @NotNull Map<<warning descr="Consider using 'String' instead of 'Language' as the map key">? super Language</warning>, Object> myLanguageMap2 = new HashMap<>();

  private final @NotNull Map<<warning descr="Consider using 'String' instead of 'FileType' as the map key">FileType</warning>, Object> myFileTypeMap = new HashMap<>();
  private final @NotNull TreeMap<<warning descr="Consider using 'String' instead of 'FileType' as the map key">FileType</warning>, Object> myFileTypeTreeMap = new TreeMap<>();
  private final @NotNull Map<<warning descr="Consider using 'String' instead of 'FileType' as the map key">? extends FileType</warning>, Object> myFileTypeMap2 = new HashMap<>();
  private final @NotNull Map<<warning descr="Consider using 'String' instead of 'LanguageFileType' as the map key">LanguageFileType</warning>, Object> myLanguageFileTypeMap = new HashMap<>();

  private final @NotNull Map<Object, Object> objectMap = new HashMap<>();
  private final @NotNull TreeMap<Object, Object> objectTreeMap = new TreeMap<>();
}