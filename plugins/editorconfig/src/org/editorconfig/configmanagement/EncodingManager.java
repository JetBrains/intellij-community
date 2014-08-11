package org.editorconfig.configmanagement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EncodingManager extends FileDocumentManagerAdapter {
  // Handles the following EditorConfig settings:
  private static final String charsetKey = "charset";

  private final Logger LOG = Logger.getInstance("#org.editorconfig.codestylesettings.EncodingManager");
  private final Project project;

  private static final Map<String, Charset> encodingMap;

  static {
    Map<String, Charset> map = new HashMap<String, Charset>();
    map.put("latin1", Charset.forName("ISO-8859-1"));
    map.put("utf-8", Charset.forName("UTF-8"));
    map.put("utf-16be", Charset.forName("UTF-16BE"));
    map.put("utf-16le", Charset.forName("UTF-16LE"));
    encodingMap = Collections.unmodifiableMap(map);
  }

  private boolean isApplyingSettings;

  public EncodingManager(Project project) {
    this.project = project;
    isApplyingSettings = false;
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (!isApplyingSettings) {
      applySettings(file);
    }
  }

  private void applySettings(VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) return;
    // Prevent "setEncoding" calling "saveAll" from causing an endless loop
    isApplyingSettings = true;
    final String filePath = file.getCanonicalPath();
    final List<OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(filePath);
    final EncodingProjectManager encodingProjectManager = EncodingProjectManager.getInstance(project);
    final String charset = Utils.configValueForKey(outPairs, charsetKey);
    if (!charset.isEmpty()) {
      if (encodingMap.containsKey(charset)) {
        encodingProjectManager.setEncoding(file, encodingMap.get(charset));
        LOG.debug(Utils.appliedConfigMessage(charset, charsetKey, filePath));
      }
      else {
        LOG.warn(Utils.invalidConfigMessage(charset, charsetKey, filePath));
      }
    }
    isApplyingSettings = false;
  }
}
