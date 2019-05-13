import javax.microedition.rms.RecordStore;

class RecordStoreResource {

  void f() {
    RecordStore rs = RecordStore.<warning descr="'RecordStore' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">openRecordStore</warning>("popeye", true);
    rs.getRecord(1);
    rs.closeRecordStore();
  }

  void g() {
    RecordStore rs = RecordStore.openRecordStore("popeye", true);
    try {
      rs.getRecord(1);
    } finally {
      rs.closeRecordStore();
    }
  }

  void h() {
    RecordStore rs = null;
    try {
      rs = RecordStore.openRecordStore("popeye", true);
      rs.getRecord(1);
    } finally {
      if (rs != null) {
        rs.closeRecordStore();
      }
    }
  }
}