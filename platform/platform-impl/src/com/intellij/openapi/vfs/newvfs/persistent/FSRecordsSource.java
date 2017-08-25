package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.cassandra.CassandraIndexTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface FSRecordsSource {

  SourceInfo connect();

  int[] list(int id);

  TIntArrayList getAllAncestors(TIntArrayList ids);

  List<RecordInfo> loadRecords(TIntArrayList ids);

  ByteBuffer getContent(int fileId);

  ByteBuffer readAttr(int fileId, String attrId, int version);

  class SourceInfo {
    final int maxId;
    final Map<String, Integer> roots;

    public SourceInfo(int maxId, final Map<String, Integer> roots) {
      this.maxId = maxId;
      this.roots = roots;
    }
  }

  class TombStone {
    public final int id;
    public final int parentId;

    public TombStone(int id, int parentId) {
      this.id = id;
      this.parentId = parentId;
    }
  }

  class RecordInfo {
    public final int id;
    public final String name;
    public final long timestamp;
    public final long length;
    public final int flags;
    public final int parentId;
    public final TIntArrayList parents;

    public RecordInfo(int id, String name, long timestamp, long length, int flags, int parentId, TIntArrayList parents) {
      this.id = id;
      this.name = name;
      this.timestamp = timestamp;
      this.length = length;
      this.flags = flags;
      this.parentId = parentId;
      this.parents = parents;
    }
  }

  class CassandraFSRecordsSource implements FSRecordsSource {

    private final int myIndexingSession;
    private final int myShardId;

    public CassandraFSRecordsSource(int indexingSession, int shardId) {

      myIndexingSession = indexingSession;
      myShardId = shardId;
    }

    @Override
    public SourceInfo connect() {
      CassandraIndexTable cit = CassandraIndexTable.getInstance();
      int maxId = cit.getMaxId(myIndexingSession, myShardId);
      Map<String, Integer> roots = cit.getRoots(myIndexingSession, myShardId);
      return new SourceInfo(maxId, roots);
    }

    @Override
    public int[] list(int id) {
      return CassandraIndexTable.getInstance().list(myShardId, myIndexingSession, id);
    }

    @Override
    public TIntArrayList getAllAncestors(TIntArrayList ids) {
      List<Integer> idsList = new ArrayList<>();
      ids.forEach(idsList::add);
      List<List<Integer>> a = CassandraIndexTable.getInstance().getAllAncestors(myShardId, idsList);
      TIntHashSet seen = new TIntHashSet();
      TIntArrayList result = new TIntArrayList();
      for (List<Integer> p : a) {
        for (int j = p.size() - 1; j >= 0; j--) {
          Integer i = p.get(j);
          if (seen.add(i)) {
            result.add(i);
          }
        }
      }
      return result;
    }

    @Override
    public List<RecordInfo> loadRecords(TIntArrayList ids) {
      List<Integer> idsList = new ArrayList<>();
      ids.forEach(idsList::add);
      return CassandraIndexTable.getInstance().loadFSRecords(myShardId, myIndexingSession, idsList);
    }

    @Override
    public ByteBuffer getContent(int fileId) {
      return CassandraIndexTable.getInstance().getContent(myShardId, fileId);
    }

    @Override
    public ByteBuffer readAttr(int fileId, String attrId, int version) {
      return CassandraIndexTable.getInstance().readAttr(myShardId, fileId, attrId);
    }
  }

  class FSRecordsSourceImpl implements FSRecordsSource {

    private final IFSRecords myRecords;
    private PersistentStringEnumerator mySourceNames;
    private int myMaxId;

    FSRecordsSourceImpl(IFSRecords records, PersistentStringEnumerator sourceNames) {
      myRecords = records;
      mySourceNames = sourceNames;
    }

    @Override
    public SourceInfo connect() {
      IFSRecords.RootRecord[] roots = myRecords.listRoots();
      HashMap<String, Integer> rootsMap = new HashMap<>();
      for (IFSRecords.RootRecord root : roots) {
        rootsMap.put(root.url, root.id);
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
    public List<RecordInfo> loadRecords(TIntArrayList ids) {
      List<RecordInfo> infos = new ArrayList<>(ids.size());
      ids.forEach(id -> infos.add(new RecordInfo(id,
                                                 myRecords.getName(id),
                                                 myRecords.getTimestamp(id),
                                                 myRecords.getLength(id),
                                                 myRecords.getFlags(id),
                                                 myRecords.getParent(id),
                                                 myRecords.getParents(id, value -> false))));
      return infos;
    }

    @Override
    public ByteBuffer getContent(int fileId) {
      try {
        DataInputStream stream = myRecords.readContent(fileId);
        if (stream == null) {
          return null;
        }
        return ByteBuffer.wrap(FileUtil.loadBytes(stream));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public ByteBuffer readAttr(int fileId, String attrId, int version) {
      DataInputStream stream = myRecords.readAttribute(fileId, new FileAttribute(attrId, version));
      if (stream == null){
        return null;
      }
      try {
        return ByteBuffer.wrap(FileUtil.loadBytes(stream));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

}