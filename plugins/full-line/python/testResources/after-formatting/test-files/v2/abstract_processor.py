import abc
import gzip
import logging
import multiprocessing
import os
from multiprocessing import Pool
from typing import Iterator, List, Type, Optional
from tqdm.auto import tqdm
from data_processing.datapoint import CodeDataPoint
from utils.tqdm_to_logger import TqdmToLogger
logger = logging.getLogger(__name__)
class ProcessorBase(abc.ABC):⇥def process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._process(dp)⇤def process_parallel(⇥self, dps: List[CodeDataPoint], num_workers: int = -1, verbose: bool = True⇤) -> List[CodeDataPoint]:⇥"""
Processes data points in-place.
Takes N data points and returns M of them:⇥- it can be N < M since it can filter out some data points
- it can be N > M since it can split one data point into several
- it can be N = M⇤"""
return self._process_parallel(dps, num_workers, verbose)⇤@abc.abstractmethod
def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def must_be_after() -> List[Type["ProcessorBase"]]:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def must_be_before() -> List[Type["ProcessorBase"]]:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def is_deterministic() -> bool:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def is_slow() -> bool:⇥raise NotImplementedError⇤def _process_parallel(self, dps: List[CodeDataPoint], num_workers: int, verbose: bool) -> List[CodeDataPoint]:⇥assert -1 <= num_workers <= multiprocessing.cpu_count()
assert num_workers != 0
num_workers = multiprocessing.cpu_count() - 1 if num_workers == -1 else num_workers
if len(dps) > self._chunk_size * num_workers / 2:⇥with Pool(processes=num_workers) as pool:⇥async_results = []
for i in range(0, len(dps), self._chunk_size):⇥chunk = dps[i : i + self._chunk_size]
async_results.append(pool.map_async(self._process, chunk, chunksize=self._chunk_size))⇤result_dps: List[CodeDataPoint] = []
with tqdm(⇥total=len(dps),
desc=f"Processing {self.__class__.__name__}...",
disable=not verbose,
file=TqdmToLogger(logger, level=logging.INFO),⇤) as bar:⇥for async_result in async_results:⇥try:⇥results = async_result.get(self._timeout * self._chunk_size)
result_dps.extend(result for result in results if result is not None)⇤except multiprocessing.context.TimeoutError:⇥logger.warning(⇥f"Chunk exceeded {self._timeout} timeout with {self.__class__.__name__} processor"⇤)⇤bar.update(self._chunk_size)⇤⇤⇤return result_dps⇤else:⇥results = map(⇥self._process,
tqdm(⇥dps,
total=len(dps),
desc=f"Preprocessing {self.__class__.__name__}...",
disable=not verbose,
file=TqdmToLogger(logger, level=logging.INFO),
mininterval=self._chunk_size * num_workers,⇤),⇤)
return [dp for dp in results if dp is not None]⇤⇤@property
def _timeout(self) -> float:⇥return 0.5⇤@property
def _chunk_size(self) -> int:⇥return 100⇤⇤class TrainableProcessorBase(ProcessorBase):⇥def __init__(self, dir_path: str):⇥path = self.get_path(dir_path)
assert os.path.exists(path) or os.path.exists(f"{path}.gz")
if os.path.exists(path):⇥self._load(path)⇤elif os.path.exists(f"{path}.gz"):⇥with gzip.open(f"{path}.gz", "r") as f_gz:⇥with open(path, "w") as f:⇥f.write(f_gz.read().decode("utf-8"))⇤⇤self._load(path)
os.remove(path)⇤⇤@classmethod
def train(cls, dir_path: str, dps: Iterator[CodeDataPoint], **train_kwargs) -> None:⇥path = cls.get_path(dir_path)
assert not os.path.exists(path)
cls._train(dps, path, **train_kwargs)⇤@staticmethod
@abc.abstractmethod
def _train(dps: Iterator[CodeDataPoint], save_path: str, **train_kwargs) -> None:⇥raise NotImplementedError()⇤@abc.abstractmethod
def _load(self, path: str) -> None:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def get_path(dir_path: str) -> str:⇥raise NotImplementedError⇤@staticmethod
@abc.abstractmethod
def requires_data_split() -> bool:⇥raise NotImplementedError⇤⇤