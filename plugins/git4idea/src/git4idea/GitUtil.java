package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.ui.Messages;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.io.File;

import git4idea.vfs.GitFileSystem;
import git4idea.i18n.GitBundle;

/**
 * Git utility/helper methods
 */
public class GitUtil {

    @NotNull
    public static VirtualFile getVcsRoot(@NotNull final Project project, @NotNull final FilePath filePath) {
        VirtualFile vfile = VcsUtil.getVcsRootFor(project, filePath);
        if (vfile == null)
            vfile = GitFileSystem.getInstance().findFileByPath(project, filePath.getPath());

        return vfile;
    }

    @NotNull
    public static VirtualFile getVcsRoot(@NotNull final Project project, @NotNull final VirtualFile virtualFile) {
        String vpath = virtualFile.getPath();
        ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(project);
        VcsRoot[] vroots = mgr.getAllVcsRoots();
        for (VcsRoot vroot : vroots) {
            if (vroot == null) continue;
            String rootpath = vroot.path.getPath();
            if (vpath.startsWith(rootpath))
                return vroot.path;
        }

        // best guess....
        return vroots[0].path;
    }

    @NotNull
    public static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(
            @NotNull Project project,
            @NotNull List<VirtualFile> virtualFiles) {
        Map<VirtualFile, List<VirtualFile>> result = new HashMap<VirtualFile, List<VirtualFile>>();

        for (VirtualFile file : virtualFiles) {
            final VirtualFile vcsRoot = getVcsRoot(project, file);

            List<VirtualFile> files = result.get(vcsRoot);
            if (files == null) {
                files = new ArrayList<VirtualFile>();
                result.put(vcsRoot, files);
            }
            files.add(file);
        }

        return result;
    }

    @NotNull
    public static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(
            @NotNull Project project,
            @NotNull Collection<VirtualFile> virtualFiles) {
        return sortFilesByVcsRoot(project, new LinkedList<VirtualFile>(virtualFiles));
    }

    @NotNull
    public static Set<VirtualFile> getVcsRootsForFiles(Project project, VirtualFile[] affectedFiles) {
        Set<VirtualFile> roots = new HashSet<VirtualFile>();
        for (VirtualFile file : affectedFiles) {
            if (file == null) continue;
            roots.add(getVcsRoot(project, file));
        }
        return roots;
    }

    @NotNull
    public static Map<VirtualFile, List<VirtualFile>> sortFilesByVcsRoot(Project project, VirtualFile[] affectedFiles) {
        return sortFilesByVcsRoot(project, Arrays.asList(affectedFiles));
    }

    /**
     * Show error associated with the specified operation
     *
     * @param project   the project
     * @param ex        an exception
     * @param operation the operation name
     */
    public static void showOperationError(final Project project, final VcsException ex, @NonNls final String operation) {
        Messages.showErrorDialog(project, ex.getMessage(), GitBundle.message("error.occurred.during", operation));
    }

    /**
     * @return a temporary directory to use
     * @throws VcsException if an error occurs
     */
    @NotNull
    public static VirtualFile getTempDir() throws VcsException {
        try {
            @SuppressWarnings({"HardCodedStringLiteral"}) File temp = File.createTempFile("git-temp-file", "txt");
            try {
                final File parentFile = temp.getParentFile();
                if (parentFile == null) {
                    throw new Exception(GitBundle.message("missing.parent", temp));
                }
                final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(parentFile);
                if (vFile == null) {
                    throw new Exception(GitBundle.message("missing.virtual.file.for.dir",parentFile));
                }
                return vFile;
            }
            finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
        catch (Exception e) {
            throw new VcsException(GitBundle.message("cannot.locate.tempdir"), e);
        }
    }

}