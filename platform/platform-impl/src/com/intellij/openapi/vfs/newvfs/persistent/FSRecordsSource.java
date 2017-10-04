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

  List<RecordInfo> loadRecords(TIntArrayList ids);

  ByteBuffer getContent(int fileId);

  ByteBuffer readAttr(int fileId, String attrId, int version);

  List<Integer> getTree();

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

    public RecordInfo(int id, String name, long timestamp, long length, int flags) {
      this.id = id;
      this.name = name;
      this.timestamp = timestamp;
      this.length = length;
      this.flags = flags;
    }
  }

  class MountPoint {
    int fileId;
    int shardId;
    public MountPoint(int fileId, int shardId) {
      this.fileId = fileId;
      this.shardId = shardId;
    }

    @Override
    public int hashCode() {
      return FSRecordsShard.addShardId(fileId, shardId);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MountPoint && obj.hashCode() == hashCode();
    }
  }

  class CassandraFSRecordsSource implements FSRecordsSource {

    private final int myIndexingSession;
    private final int myShardId;

    public int getShard() {
      return myShardId;
    }

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

    @Override
    public List<Integer> getTree() {
      return CassandraIndexTable.getInstance().getTree(myShardId, myIndexingSession);
    }
  }
}