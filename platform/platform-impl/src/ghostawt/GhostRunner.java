// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

public class GhostRunner {
    public static void main(String[] args) {
        if (args.length == 0) { return; }

        String ghostAwtLibs;
        try {
            ghostAwtLibs = getNativeLibPath();
        } catch (Exception e) {
            System.err.println("Could not find native library path for current platform, please specify with -Dghostawt.library.path=/path/to/libs ");
            System.exit(1);
            return;
        }

        // setup ghost properties
        System.setProperty("ghostawt", "true");
        System.setProperty("awt.toolkit", "ghostawt.GhostToolkit");
        System.setProperty("java.awt.graphicsenv", "ghostawt.image.GhostGraphicsEnvironment");
        System.setProperty("java.awt.headless", "false");
        System.setProperty("java2d.font.usePlatformFont", "true");
        System.setProperty("sun.font.fontmanager", "ghostawt.sun.GFontManager");
        String[] libraryPath = addBootLibraryPath(ghostAwtLibs);
        copyLibToLocal(libraryPath, ghostAwtLibs, "t2k");

        // built application setup
        String mainClass;
        String[] appArgs;

        mainClass = args[0];
        appArgs = new String[args.length - 1];
        if (appArgs.length > 0) {
            System.arraycopy(args, 1, appArgs, 0, appArgs.length);
        }

        // call main
        try {
            Class<?> clz = Class.forName(mainClass);
            Method main = clz.getMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, new Object[] { appArgs });
        } catch (ClassNotFoundException e) {
            System.err.println("Could not find main class");
            e.printStackTrace();
            System.exit(1);
        } catch (NoSuchMethodException e) {
            System.err.println("Could not find main method in main class");
            e.printStackTrace();
            System.exit(1);
        } catch (SecurityException e) {
            System.err.println("Could not access main method in main class");
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalAccessException e) {
            System.err.println("Could not access main method in main class");
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Could not call main method in main class");
            e.printStackTrace();
            System.exit(1);
        } catch (InvocationTargetException e) {
            System.err.println("Could not call main method in main class");
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static void copyLibToLocal(String[] paths, String target, String libName) {
        for (int i = 0; i < paths.length; i++) {
            File libfile = new File(paths[i], System.mapLibraryName(libName));
            if (libfile.exists()) {
                File targetLib = new File(target, libfile.getName());
                if (!targetLib.exists()) {
                    try {
                        Files.copy(libfile.toPath(), targetLib.toPath());
                    } catch (IOException e) {
                        System.err.printf("Could not copy lib %s to local", libName);
                        System.out.println();
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String[] addBootLibraryPath(String newPath) {
        System.setProperty("sun.boot.library.path", newPath + File.pathSeparator + System.getProperty("sun.boot.library.path"));

        String[] newPaths = null;
        try {
            // update internal array if property was already loaded
            final Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
            sysPathsField.setAccessible(true);

            String[] paths = (String[]) sysPathsField.get(null);
            if (paths != null) {
                // no duplicates
                for (String path : paths) {
                    if (path.equals(newPath)) { return paths; }
                }
                // add new item
                newPaths = new String[paths.length + 1];
                newPaths[0] = newPath;
                System.arraycopy(paths, 0, newPaths, 1, paths.length);
                sysPathsField.set(null, newPaths);
            }
        } catch (Exception e) {
            // should never happen
        }

        return newPaths;
    }

    private static String getNativeLibPath() {
        // check user setting
        String path = System.getProperty("ghostawt.library.path");
        if (path != null) { return path; }

        // try to get base directory of GhostAWT
        // from jar/.class file
        path = ".";

        // if no user setting we try to find the location of the
        // jar/class file
        URL classSource = GhostRunner.class.getProtectionDomain().getCodeSource().getLocation();
        File sourceFile;
        try {
            sourceFile = new File(classSource.toURI());
            path = sourceFile.getParentFile().getAbsolutePath();
        } catch (URISyntaxException e) {
        }

        return getNativeLibPath(path);
    }

    private static String getNativeLibPath(String baseDir) {
        String arch = System.getProperty("os.arch");
        return new File(baseDir, arch).getAbsolutePath();
    }
}
