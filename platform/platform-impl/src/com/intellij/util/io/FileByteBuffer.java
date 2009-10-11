/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FileByteBuffer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.FileByteBuffer");
  
  private final RandomAccessFile myFile;

  public FileByteBuffer(RandomAccessFile file) {
    myFile = file;
  }

  public byte get() {
    try{
      return myFile.readByte();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer put(byte b) {
    try{
      myFile.write(b);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public byte get(int index) {
    try{
      myFile.seek(index);
      return myFile.readByte();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer put(int index, byte b) {
    try{
      myFile.seek(index);
      myFile.write(b);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public char getChar() {
    try{
      return myFile.readChar();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putChar(char value) {
    try{
      myFile.writeChar(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public char getChar(int index) {
    try{
      myFile.seek(index);
      return myFile.readChar();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putChar(int index, char value) {
    try{
      myFile.seek(index);
      myFile.writeChar(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public short getShort() {
    try{
      return myFile.readShort();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putShort(short value) {
    try{
      myFile.writeShort(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public short getShort(int index) {
    try{
      myFile.seek(index);
      return myFile.readShort();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putShort(int index, short value) {
    try{
      myFile.seek(index);
      myFile.writeShort(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public int getInt() {
    try{
      return myFile.readInt();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putInt(int value) {
    try{
      myFile.writeInt(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public int getInt(int index) {
    try{
      myFile.seek(index);
      return myFile.readInt();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putInt(int index, int value) {
    try{
      myFile.seek(index);
      myFile.writeInt(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public long getLong() {
    try{
      return myFile.readLong();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putLong(long value) {
    try{
      myFile.writeLong(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public long getLong(int index) {
    try{
      myFile.seek(index);
      return myFile.readLong();
    }
    catch(IOException e){
      LOG.error(e);
      return 0;
    }
  }

  public FileByteBuffer putLong(int index, long value) {
    try{
      myFile.seek(index);
      myFile.writeLong(value);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }

  public void position(int index) {
    try{
      myFile.seek(index);
    }
    catch(IOException e){
      LOG.error(e);
    }
  }

  public void put(byte[] src, int offset, int length) {
    try{
      myFile.write(src, offset, length);
    }
    catch(IOException e){
      LOG.error(e);
    }
  }

  public FileByteBuffer get(byte[] dst, int offset, int length) {
    try{
      myFile.read(dst, offset, length);
    }
    catch(IOException e){
      LOG.error(e);
    }
    return this;
  }
}
