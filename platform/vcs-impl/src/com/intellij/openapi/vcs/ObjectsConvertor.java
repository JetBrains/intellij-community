package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ObjectsConvertor {
  public static final Convertor<FilePath, VirtualFile> FILEPATH_TO_VIRTUAL = new Convertor<FilePath, VirtualFile>() {
    public VirtualFile convert(FilePath fp) {
      return fp.getVirtualFile();
    }
  };

  public static final Convertor<VirtualFile, FilePath> VIRTUAL_FILEPATH = new Convertor<VirtualFile, FilePath>() {
    public FilePath convert(VirtualFile vf) {
      return new FilePathImpl(vf);
    }
  };

  public static final Convertor<FilePath, File> FILEPATH_FILE = new Convertor<FilePath, File>() {
    public File convert(FilePath fp) {
      return fp.getIOFile();
    }
  };

  public static final Convertor<File, FilePath> FILE_FILEPATH = new Convertor<File, FilePath>() {
    public FilePath convert(File file) {
      return FilePathImpl.create(file);
    }
  };

  public static final NotNullFunction<Object, Boolean> NOT_NULL = new NotNullFunction<Object, Boolean>() {
    @NotNull
    public Boolean fun(final Object o) {
      return o != null;
    }
  };

  public static List<VirtualFile> fp2vf(final Collection<FilePath> in) {
    return convert(in, FILEPATH_TO_VIRTUAL);
  }

  public static List<FilePath> vf2fp(final List<VirtualFile> in) {
    return convert(in, VIRTUAL_FILEPATH);
  }

  public static List<File> fp2jiof(final Collection<FilePath> in) {
    return convert(in, FILEPATH_FILE);
  }

  public static <T,S> List<S> convert(final Collection<T> in, final Convertor<T,S> convertor) {
    return convert(in, convertor, null);
  }

  public static <T,U, S extends U> List<S> convert(final Collection<T> in, final Convertor<T,S> convertor,
                                      @Nullable final NotNullFunction<U, Boolean> outFilter) {
    final List<S> out = new ArrayList<S>();
    for (T t : in) {
      final S converted = convertor.convert(t);
      if ((outFilter != null) && (! Boolean.TRUE.equals(outFilter.fun(converted)))) continue;
      out.add(converted);
    }
    return out;
  }
}
