big_endian

# mv stands for MVStore
requires 0 "6D 76"

proc header {} {
  section "Header" {
    ascii 2 "Magic Bytes"

    uint8 "Write Format Version"
    uint8 "Read Format Version"

    int64 "Creation Time"

    int64 "Last Chunk Version"
    int32 "Last Chunk Id"
    int64 "Last Chunk Block Number"

    int32 "Block Size"

    uint8 "Clean Shutdown"
    int32 "Checksum"
  }
}

proc chunk {} {
  section "Chunk" {
    set chunkStartPosition [pos]

    int32 "Id"
    int32 "Map Id"
    # start block number within the file
    int64 "Start Block Number"
    int32 "ToC Position"

    set blockCount [int32 "Block Count"]
    int64 "Max Length Sum of All Pages"
    int32 "Page Count"

    int64 "Next Chunk Predicted Position"
    int64 "layoutRootPos"

    int64 "Time"
    int64 "Version"

    section "Page" {
      int32 "Size"
      int16 "Checksum"
      varint "Map Id"
      varint "Page No"
      varint "Key Count"
      bytes 1 "Type"
    }

    goto [expr $chunkStartPosition + (4096 * $blockCount) - 24]
    section "Chunk Footer" {
      int32 "Id"
      int64 "Block Number"
      int64 "Version"
      int32 "Checksum"
    }
  }
}

proc varint { {label ""} {_extra ""} } {
    set n 0
    for { set i 0 } { 1 } { incr i } {
        set b [uint8]
        set n [expr $n | (($b & 0x7f) << (7*$i))]
        if { !($b & 0x80) } {
            break
        }
    }

    set len [expr $i+1]
    if { $label != "" } {
        entry $label $n $len [expr [pos]-$len]
    }

    return [list $len $n]
}

set blockSize 4096

header
goto $blockSize
header
goto [expr $blockSize * 2]

while {![end]} {
  chunk
}
