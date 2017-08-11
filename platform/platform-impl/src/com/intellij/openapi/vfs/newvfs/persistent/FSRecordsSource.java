package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

interface FSRecordsSource {

  SourceInfo connect();

  int[] list(int id);

  TIntArrayList getAllAncestors(TIntArrayList ids);

  RecordInfo[] loadRecords(TIntArrayList ids);

  byte[] getContent(int fileId);

  byte[] readAttr(int fileId, String attrId, int version);

  class SourceInfo {
    final int maxId;
    final Map<String, Integer> roots;

    public SourceInfo(int maxId, final Map<String, Integer> roots) {
      this.maxId = maxId;
      this.roots = roots;
    }
  }

  class RecordInfo {
    final int id;
    final String name;
    final long timestamp;
    final long length;
    final int flags;
    final int parentId;

    public RecordInfo(int id, String name, long timestamp, long length, int flags, int parentId) { this.id = id;
      this.name = name;
      this.timestamp = timestamp;
      this.length = length;
      this.flags = flags;
      this.parentId = parentId;
    }
  }

  class FSRecordsSourceImpl implements FSRecordsSource {

    private final IFSRecords myRecords;
    private int myMaxId;

    FSRecordsSourceImpl(IFSRecords records) {
      myRecords = records;
    }

    @Override
    public SourceInfo connect() {
      int[] roots = myRecords.listRoots();
      HashMap<String, Integer> rootsMap = new HashMap<>();
      for (int root : roots) {
        rootsMap.put(myRecords.getName(root), root);
      }
      myMaxId = ((FSRecords)myRecords).getMaxId();
      return new SourceInfo(myMaxId, rootsMap);
    }

    @Override
    public int[] list(int id) {
      return myRecords.list(id);
    }

    @Override
    public TIntArrayList getAllAncestors(TIntArrayList ids) {
      TIntHashSet visited = new TIntHashSet();
      TIntArrayList result = new TIntArrayList();
      ids.forEach(id -> {
        TIntArrayList parents = myRecords.getParents(id, value -> visited.contains(value));
        for (int i = parents.size() - 1; i >= 0; i--) {
          result.add(parents.get(i));
          visited.add(parents.get(i));
        }
        return true;
      });
      return result;
    }

    @Override
    public RecordInfo[] loadRecords(TIntArrayList ids) {
      RecordInfo[] infos = new RecordInfo[ids.size()];
      for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        infos[i] = new RecordInfo(id,
                                  myRecords.getName(id),
                                  myRecords.getTimestamp(id),
                                  myRecords.getLength(id),
                                  myRecords.getFlags(id),
                                  myRecords.getParent(id));
      }
      return infos;
    }

    @Override
    public byte[] getContent(int fileId) {
      try {
        DataInputStream stream = myRecords.readContent(fileId);
        if (stream == null) {
          return null;
        }
        return FileUtil.loadBytes(stream);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public byte[] readAttr(int fileId, String attrId, int version) {
      DataInputStream stream = myRecords.readAttribute(fileId, new FileAttribute(attrId, version));
      if (stream == null){
        return null;
      }
      try {
        return FileUtil.loadBytes(stream);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

}