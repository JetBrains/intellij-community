// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.ActionLink;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.intellij.find.findUsages.similarity.ExportClusteringResultActionLink.*;


public class ImportClusteringResultActionLink extends ActionLink {
  private static final Logger LOG = Logger.getLogger(ImportClusteringResultActionLink.class.getName());

  public ImportClusteringResultActionLink(@NotNull Project project, @NotNull ClusteringSearchSession session,
                                          @NotNull RefreshAction refreshAction) {
    super(UsageViewBundle.message("similar.usages.internal.import.clustering.data"),
          (event) -> {
            final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
            fileChooserDescriptor.setTitle(UsageViewBundle.message("similar.usages.internal.import.clustering.data.title"));
            final VirtualFile file = FileChooser.chooseFile(fileChooserDescriptor, project, null);
            if (file == null) return;
            try {
              Map<String, SimilarUsage> usageIndex = buildIndex(session);
              session.updateClusters(parseFile(file, usageIndex));
              refreshAction.actionPerformed(ActionUtil.createEmptyEvent());
            }
            catch (IOException ioe) {
              throw new RuntimeException(ioe);
            }
          });
  }

  private static Map<String, SimilarUsage> buildIndex(@NotNull ClusteringSearchSession session) {
    List<SimilarUsage> usages = session.getClusters().stream().flatMap(e -> e.getUsages().stream()).toList();
    return usages.stream()
      .collect(Collectors.toMap(e -> getUsageId(Objects.requireNonNull(((UsageInfo2UsageAdapter)e).getElement())), value -> value,
                                (usageId1, usageId2) ->
                                {
                                  LOG.warning("Duplicate found:" + usageId1);
                                  return usageId1;
                                }));
  }

  private static @NotNull Collection<UsageCluster> parseFile(@NotNull VirtualFile file, @NotNull Map<String, SimilarUsage> usageIndex)
    throws IOException {
    JsonReaderEx json = new JsonReaderEx(VfsUtilCore.loadText(file));
    List<Map<String, Object>> list = JsonUtil.nextList(json);
    Int2ObjectMap<UsageCluster> clusters = new Int2ObjectOpenHashMap<>();
    list.forEach(e -> {
      int cluster_number = (int)(double)e.get(CLUSTER_NUMBER);
      UsageCluster cluster = clusters.get(cluster_number);
      if (cluster == null) {
        cluster = new UsageCluster();
        clusters.put(cluster_number, cluster);
      }
      Object filename = e.get(FILENAME);
      if (filename != null) {
        SimilarUsage usage = usageIndex.get(filename);
        if (usage == null) {
          LOG.warning("No usage found for " + filename);
        }
        else {
          cluster.addUsage(usage);
        }
      }
    });
    return clusters.values();
  }
}
